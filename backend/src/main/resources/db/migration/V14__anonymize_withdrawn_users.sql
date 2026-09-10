UPDATE users
SET email = 'deleted+' || id::text || '@users.invalid',
    display_name = '탈퇴한 사용자',
    password_hash = NULL,
    profile_image_id = NULL
WHERE status = 'WITHDRAWN';
