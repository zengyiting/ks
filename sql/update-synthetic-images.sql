-- Update synthetic items with category-appropriate images from Unsplash
UPDATE items SET image_url = 'https://images.unsplash.com/photo-1518770660439-4636190af475?w=400&h=300&fit=crop' WHERE category = 'electronics' AND image_url IS NULL;
UPDATE items SET image_url = 'https://images.unsplash.com/photo-1556909114-f6e7ad7d3136?w=400&h=300&fit=crop' WHERE category = 'kitchen' AND image_url IS NULL;
UPDATE items SET image_url = 'https://images.unsplash.com/photo-1517836357463-d25dfeac3438?w=400&h=300&fit=crop' WHERE category = 'sports' AND image_url IS NULL;
UPDATE items SET image_url = 'https://images.unsplash.com/photo-1596462502278-27bfdc403348?w=400&h=300&fit=crop' WHERE category = 'beauty' AND image_url IS NULL;
UPDATE items SET image_url = 'https://images.unsplash.com/photo-1495446815901-a7297e633e8d?w=400&h=300&fit=crop' WHERE category = 'books' AND image_url IS NULL;
UPDATE items SET image_url = 'https://images.unsplash.com/photo-1558060370-d644479cb6f7?w=400&h=300&fit=crop' WHERE category = 'toy' AND image_url IS NULL;
UPDATE items SET image_url = 'https://images.unsplash.com/photo-1555041469-a586c61ea9bc?w=400&h=300&fit=crop' WHERE category = 'furniture' AND image_url IS NULL;
UPDATE items SET image_url = 'https://images.unsplash.com/photo-1497032628192-86f99bcd76bc?w=400&h=300&fit=crop' WHERE category = 'office' AND image_url IS NULL;
