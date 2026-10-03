SET NAMES utf8mb4;

UPDATE devices SET 
    category = 'Khu vực sân sau',
    location = 'Ngoài vườn'
WHERE id = 1;

UPDATE devices SET 
    category = 'Phòng khách',
    location = 'Trong nhà'
WHERE id = 2;

UPDATE devices SET 
    category = 'Phân xưởng A',
    location = 'Nhà máy'
WHERE id = 3;
