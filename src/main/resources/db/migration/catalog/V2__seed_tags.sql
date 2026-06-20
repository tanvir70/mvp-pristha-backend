INSERT INTO catalog.tags (name, slug, created_at, updated_at)
VALUES
    ('Fiction', 'fiction', NOW(), NOW()),
    ('Poetry', 'poetry', NOW(), NOW()),
    ('Essay', 'essay', NOW(), NOW()),
    ('Daily Thought', 'daily-thought', NOW(), NOW())
ON CONFLICT (slug) DO NOTHING;
