# Extract clean data from Tokopedia CSV
import csv
import re

input_file = 'd:/app/ks/recommendtwo/sql/tokopedia_products.csv'
output_file = 'd:/app/ks/recommendtwo/sql/items_clean.csv'

with open(input_file, 'r', encoding='utf-8') as f:
    reader = csv.DictReader(f)
    rows = []
    for row in reader:
        name = row.get('title', '')[:200] if row.get('title') else ''
        if not name:
            continue
        category = row.get('categories', 'Uncategorized')[:100] if row.get('categories') else 'Uncategorized'
        price_str = re.sub(r'[^0-9.]', '', row.get('final_price', '0') or '0')
        try:
            price = float(price_str) if price_str else 0
        except:
            price = 0
        image_url = row.get('main_image', '')[:255] if row.get('main_image') else ''
        description = (row.get('description', '') or '')[:2000]
        rows.append([name, category, price, image_url, description])

with open(output_file, 'w', encoding='utf-8', newline='') as f:
    writer = csv.writer(f)
    writer.writerow(['name', 'category', 'price', 'image_url', 'description'])
    writer.writerows(rows)

print(f'Generated {len(rows)} items')