import pdfplumber
import io
import json

pdf_path = "docs/基于协同过滤的个性化电影推荐系统_刘晓伟.pdf"

all_text = []

with pdfplumber.open(pdf_path) as pdf:
    print(f"PDF页数: {len(pdf.pages)}")

    for i in range(len(pdf.pages)):
        page = pdf.pages[i]
        text = page.extract_text()
        if text:
            all_text.append({
                "page": i + 1,
                "text": text
            })

# 保存到JSON文件（UTF-8编码）
with open("docs/pdf_content.json", "w", encoding="utf-8") as f:
    json.dump(all_text, f, ensure_ascii=False, indent=2)

print("内容已保存到 docs/pdf_content.json")

# 搜索关键词
keywords = ["评估", "准确率", "精确率", "Precision", "Recall", "实验", "指标", "K=", "K ="]

for item in all_text:
    page_text = item["text"]
    for kw in keywords:
        if kw in page_text:
            print(f"\n第{item['page']}页包含关键词: {kw}")
            # 找到关键词所在位置，显示周围文字
            idx = page_text.find(kw)
            start = max(0, idx - 100)
            end = min(len(page_text), idx + 200)
            snippet = page_text[start:end]
            print(f"片段: ...{snippet}...")
            break
