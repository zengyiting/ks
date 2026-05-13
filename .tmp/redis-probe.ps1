$ErrorActionPreference = "Stop"
$hostName = "192.168.253.158"
$port = 6379
$password = "2004zyta"

function Write-Resp([System.IO.Stream]$stream, [string[]]$parts) {
  $sb = New-Object System.Text.StringBuilder
  [void]$sb.Append("*").Append($parts.Length).Append("`r`n")
  foreach($p in $parts){
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($p)
    [void]$sb.Append("$").Append($bytes.Length).Append("`r`n").Append($p).Append("`r`n")
  }
  $raw = [System.Text.Encoding]::UTF8.GetBytes($sb.ToString())
  $stream.Write($raw, 0, $raw.Length)
  $stream.Flush()
}

function Read-Line([System.IO.Stream]$stream){
  $ms = New-Object System.IO.MemoryStream
  while($true){
    $b = $stream.ReadByte()
    if($b -lt 0){ throw "connection closed" }
    if($b -eq 13){
      $lf = $stream.ReadByte()
      if($lf -ne 10){ throw "invalid line ending" }
      break
    }
    $ms.WriteByte([byte]$b)
  }
  [System.Text.Encoding]::UTF8.GetString($ms.ToArray())
}

function Read-Exact([System.IO.Stream]$stream, [int]$len){
  $buf = New-Object byte[] $len
  $off = 0
  while($off -lt $len){
    $n = $stream.Read($buf, $off, $len - $off)
    if($n -le 0){ throw "connection closed while reading bulk" }
    $off += $n
  }
  $buf
}

function Read-Resp([System.IO.Stream]$stream){
  $prefix = $stream.ReadByte()
  if($prefix -lt 0){ throw "connection closed before response" }
  switch([char]$prefix){
    "+" { return @{ type = "simple"; value = (Read-Line $stream) } }
    "-" { return @{ type = "error"; value = (Read-Line $stream) } }
    ":" { return @{ type = "integer"; value = [long](Read-Line $stream) } }
    "$" {
      $len = [int](Read-Line $stream)
      if($len -eq -1){ return @{ type = "bulk"; value = $null } }
      $data = Read-Exact $stream $len
      $cr = $stream.ReadByte(); $lf = $stream.ReadByte()
      if($cr -ne 13 -or $lf -ne 10){ throw "invalid bulk terminator" }
      return @{ type = "bulk"; value = [System.Text.Encoding]::UTF8.GetString($data) }
    }
    "*" {
      $cnt = [int](Read-Line $stream)
      $arr = @()
      for($i = 0; $i -lt $cnt; $i++){
        $arr += ,(Read-Resp $stream)
      }
      return @{ type = "array"; value = $arr }
    }
    default { throw "unexpected prefix: $prefix" }
  }
}

function Probe([string]$mode){
  $client = New-Object System.Net.Sockets.TcpClient
  $client.ReceiveTimeout = 5000
  $client.SendTimeout = 5000
  $client.Connect($hostName, $port)
  try {
    if($mode -eq "tls"){
      $stream = New-Object System.Net.Security.SslStream($client.GetStream(), $false, ({$true}))
      $stream.AuthenticateAsClient($hostName)
    } else {
      $stream = $client.GetStream()
    }

    Write-Output "MODE=$mode"
    Write-Resp $stream @("PING")
    $r1 = Read-Resp $stream
    Write-Output "PING(no-auth) => $($r1.type):$($r1.value)"

    Write-Resp $stream @("AUTH", $password)
    $r2 = Read-Resp $stream
    Write-Output "AUTH(pass) => $($r2.type):$($r2.value)"

    Write-Resp $stream @("PING")
    $r3 = Read-Resp $stream
    Write-Output "PING(post-auth) => $($r3.type):$($r3.value)"

    Write-Resp $stream @("SCAN", "0", "MATCH", "recommendationResults*", "COUNT", "100")
    $r4 = Read-Resp $stream
    if($r4.type -eq "array"){
      $cursor = $r4.value[0].value
      $keys = @()
      if($r4.value[1].type -eq "array"){
        foreach($k in $r4.value[1].value){ $keys += $k.value }
      }
      Write-Output "SCAN cursor=$cursor keys=$($keys -join ',')"
    } else {
      Write-Output "SCAN => $($r4.type):$($r4.value)"
    }
  }
  finally {
    $client.Close()
  }
}

try { Probe "plain" } catch { Write-Output ("MODE=plain ERROR=" + $_.Exception.Message) }
try { Probe "tls" } catch { Write-Output ("MODE=tls ERROR=" + $_.Exception.Message) }
