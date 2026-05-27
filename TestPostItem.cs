using System;
using System.Net;
using System.Text;

class TestPostItem {
    static void Main() {
        string url = "http://localhost:8080/api/admin/items";
        string boundary = "----TestBoundary12345";
        
        StringBuilder body = new StringBuilder();
        body.AppendLine("--" + boundary);
        body.AppendLine("Content-Disposition: form-data; name=\"name\"");
        body.AppendLine();
        body.AppendLine("TestItem");
        
        body.AppendLine("--" + boundary);
        body.AppendLine("Content-Disposition: form-data; name=\"category\"");
        body.AppendLine();
        body.AppendLine("TestCategory");
        
        body.AppendLine("--" + boundary);
        body.AppendLine("Content-Disposition: form-data; name=\"price\"");
        body.AppendLine();
        body.AppendLine("199.99");
        
        body.AppendLine("--" + boundary);
        body.AppendLine("Content-Disposition: form-data; name=\"description\"");
        body.AppendLine();
        body.AppendLine("This is a test item description");
        
        body.AppendLine("--" + boundary + "--");
        
        byte[] data = Encoding.UTF8.GetBytes(body.ToString());
        
        HttpWebRequest request = (HttpWebRequest)WebRequest.Create(url);
        request.Method = "POST";
        request.ContentType = "multipart/form-data; boundary=" + boundary;
        request.ContentLength = data.Length;
        
        using (var stream = request.GetRequestStream()) {
            stream.Write(data, 0, data.Length);
        }
        
        try {
            using (HttpWebResponse response = (HttpWebResponse)request.GetResponse()) {
                Console.WriteLine("HTTP Status: " + (int)response.StatusCode);
                using (var reader = new System.IO.StreamReader(response.GetResponseStream())) {
                    Console.WriteLine("Response: " + reader.ReadToEnd());
                }
            }
        } catch (WebException ex) {
            Console.WriteLine("Error: " + ex.Message);
            if (ex.Response != null) {
                using (var reader = new System.IO.StreamReader(ex.Response.GetResponseStream())) {
                    Console.WriteLine("Error Body: " + reader.ReadToEnd());
                }
            }
        }
    }
}