-- Mock 데이터 제거: 외부 API(알라딘, 카카오)에서만 실제 도서 데이터를 가져옵니다
-- BookDataLoaderService가 자동으로 외부 API에서 데이터를 로드합니다

-- 영진전문대학교 17개 계열/학과 초기 데이터
INSERT INTO departments (name) VALUES ('컴퓨터정보계열') ON CONFLICT (name) DO NOTHING;
INSERT INTO departments (name) VALUES ('AI융합기계계열') ON CONFLICT (name) DO NOTHING;
INSERT INTO departments (name) VALUES ('반도체전자계열') ON CONFLICT (name) DO NOTHING;
INSERT INTO departments (name) VALUES ('신재생에너지전기계열') ON CONFLICT (name) DO NOTHING;
INSERT INTO departments (name) VALUES ('건축인테리어디자인계열') ON CONFLICT (name) DO NOTHING;
INSERT INTO departments (name) VALUES ('경영회계융합계열') ON CONFLICT (name) DO NOTHING;
INSERT INTO departments (name) VALUES ('호텔항공관광과') ON CONFLICT (name) DO NOTHING;
INSERT INTO departments (name) VALUES ('국방군사계열') ON CONFLICT (name) DO NOTHING;
INSERT INTO departments (name) VALUES ('사회복지과') ON CONFLICT (name) DO NOTHING;
INSERT INTO departments (name) VALUES ('유아교육과') ON CONFLICT (name) DO NOTHING;
INSERT INTO departments (name) VALUES ('보건의료행정과') ON CONFLICT (name) DO NOTHING;
INSERT INTO departments (name) VALUES ('간호학과') ON CONFLICT (name) DO NOTHING;
INSERT INTO departments (name) VALUES ('동물보건과') ON CONFLICT (name) DO NOTHING;
INSERT INTO departments (name) VALUES ('조리제과제빵과') ON CONFLICT (name) DO NOTHING;
INSERT INTO departments (name) VALUES ('뷰티융합과') ON CONFLICT (name) DO NOTHING;
INSERT INTO departments (name) VALUES ('방송영상미디어과') ON CONFLICT (name) DO NOTHING;
INSERT INTO departments (name) VALUES ('만화애니메이션과') ON CONFLICT (name) DO NOTHING;

