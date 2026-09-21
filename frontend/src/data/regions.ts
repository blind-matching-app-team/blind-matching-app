// Shared S3/S4 mock source: backend V7__seed_region.sql. Internal IDs, not official administrative codes.
export const regions: { code: string; parent: string | null; name: string }[] = [
  {
    code: 'SEOUL',
    parent: null,
    name: '서울특별시',
  },
  {
    code: 'BUSAN',
    parent: null,
    name: '부산광역시',
  },
  {
    code: 'DAEGU',
    parent: null,
    name: '대구광역시',
  },
  {
    code: 'INCHEON',
    parent: null,
    name: '인천광역시',
  },
  {
    code: 'GWANGJU',
    parent: null,
    name: '광주광역시',
  },
  {
    code: 'DAEJEON',
    parent: null,
    name: '대전광역시',
  },
  {
    code: 'ULSAN',
    parent: null,
    name: '울산광역시',
  },
  {
    code: 'SEJONG',
    parent: null,
    name: '세종특별자치시',
  },
  {
    code: 'GYEONGGI',
    parent: null,
    name: '경기도',
  },
  {
    code: 'GANGWON',
    parent: null,
    name: '강원특별자치도',
  },
  {
    code: 'CHUNGBUK',
    parent: null,
    name: '충청북도',
  },
  {
    code: 'CHUNGNAM',
    parent: null,
    name: '충청남도',
  },
  {
    code: 'JEONBUK',
    parent: null,
    name: '전북특별자치도',
  },
  {
    code: 'JEONNAM',
    parent: null,
    name: '전라남도',
  },
  {
    code: 'GYEONGBUK',
    parent: null,
    name: '경상북도',
  },
  {
    code: 'GYEONGNAM',
    parent: null,
    name: '경상남도',
  },
  {
    code: 'JEJU',
    parent: null,
    name: '제주특별자치도',
  },
  {
    code: 'SEOUL_JONGNO',
    parent: 'SEOUL',
    name: '종로구',
  },
  {
    code: 'SEOUL_JUNG',
    parent: 'SEOUL',
    name: '중구',
  },
  {
    code: 'SEOUL_YONGSAN',
    parent: 'SEOUL',
    name: '용산구',
  },
  {
    code: 'SEOUL_SEONGDONG',
    parent: 'SEOUL',
    name: '성동구',
  },
  {
    code: 'SEOUL_GWANGJIN',
    parent: 'SEOUL',
    name: '광진구',
  },
  {
    code: 'SEOUL_DONGDAEMUN',
    parent: 'SEOUL',
    name: '동대문구',
  },
  {
    code: 'SEOUL_JUNGNANG',
    parent: 'SEOUL',
    name: '중랑구',
  },
  {
    code: 'SEOUL_SEONGBUK',
    parent: 'SEOUL',
    name: '성북구',
  },
  {
    code: 'SEOUL_GANGBUK',
    parent: 'SEOUL',
    name: '강북구',
  },
  {
    code: 'SEOUL_DOBONG',
    parent: 'SEOUL',
    name: '도봉구',
  },
  {
    code: 'SEOUL_NOWON',
    parent: 'SEOUL',
    name: '노원구',
  },
  {
    code: 'SEOUL_EUNPYEONG',
    parent: 'SEOUL',
    name: '은평구',
  },
  {
    code: 'SEOUL_SEODAEMUN',
    parent: 'SEOUL',
    name: '서대문구',
  },
  {
    code: 'SEOUL_MAPO',
    parent: 'SEOUL',
    name: '마포구',
  },
  {
    code: 'SEOUL_YANGCHEON',
    parent: 'SEOUL',
    name: '양천구',
  },
  {
    code: 'SEOUL_GANGSEO',
    parent: 'SEOUL',
    name: '강서구',
  },
  {
    code: 'SEOUL_GURO',
    parent: 'SEOUL',
    name: '구로구',
  },
  {
    code: 'SEOUL_GEUMCHEON',
    parent: 'SEOUL',
    name: '금천구',
  },
  {
    code: 'SEOUL_YEONGDEUNGPO',
    parent: 'SEOUL',
    name: '영등포구',
  },
  {
    code: 'SEOUL_DONGJAK',
    parent: 'SEOUL',
    name: '동작구',
  },
  {
    code: 'SEOUL_GWANAK',
    parent: 'SEOUL',
    name: '관악구',
  },
  {
    code: 'SEOUL_SEOCHO',
    parent: 'SEOUL',
    name: '서초구',
  },
  {
    code: 'SEOUL_GANGNAM',
    parent: 'SEOUL',
    name: '강남구',
  },
  {
    code: 'SEOUL_SONGPA',
    parent: 'SEOUL',
    name: '송파구',
  },
  {
    code: 'SEOUL_GANGDONG',
    parent: 'SEOUL',
    name: '강동구',
  },
  {
    code: 'BUSAN_JUNG',
    parent: 'BUSAN',
    name: '중구',
  },
  {
    code: 'BUSAN_SEO',
    parent: 'BUSAN',
    name: '서구',
  },
  {
    code: 'BUSAN_DONG',
    parent: 'BUSAN',
    name: '동구',
  },
  {
    code: 'BUSAN_YEONGDO',
    parent: 'BUSAN',
    name: '영도구',
  },
  {
    code: 'BUSAN_BUSANJIN',
    parent: 'BUSAN',
    name: '부산진구',
  },
  {
    code: 'BUSAN_DONGNAE',
    parent: 'BUSAN',
    name: '동래구',
  },
  {
    code: 'BUSAN_NAM',
    parent: 'BUSAN',
    name: '남구',
  },
  {
    code: 'BUSAN_BUK',
    parent: 'BUSAN',
    name: '북구',
  },
  {
    code: 'BUSAN_HAEUNDAE',
    parent: 'BUSAN',
    name: '해운대구',
  },
  {
    code: 'BUSAN_SAHA',
    parent: 'BUSAN',
    name: '사하구',
  },
  {
    code: 'BUSAN_GEUMJEONG',
    parent: 'BUSAN',
    name: '금정구',
  },
  {
    code: 'BUSAN_GANGSEO',
    parent: 'BUSAN',
    name: '강서구',
  },
  {
    code: 'BUSAN_YEONJE',
    parent: 'BUSAN',
    name: '연제구',
  },
  {
    code: 'BUSAN_SUYEONG',
    parent: 'BUSAN',
    name: '수영구',
  },
  {
    code: 'BUSAN_SASANG',
    parent: 'BUSAN',
    name: '사상구',
  },
  {
    code: 'BUSAN_GIJANG',
    parent: 'BUSAN',
    name: '기장군',
  },
  {
    code: 'DAEGU_JUNG',
    parent: 'DAEGU',
    name: '중구',
  },
  {
    code: 'DAEGU_DONG',
    parent: 'DAEGU',
    name: '동구',
  },
  {
    code: 'DAEGU_SEO',
    parent: 'DAEGU',
    name: '서구',
  },
  {
    code: 'DAEGU_NAM',
    parent: 'DAEGU',
    name: '남구',
  },
  {
    code: 'DAEGU_BUK',
    parent: 'DAEGU',
    name: '북구',
  },
  {
    code: 'DAEGU_SUSEONG',
    parent: 'DAEGU',
    name: '수성구',
  },
  {
    code: 'DAEGU_DALSEO',
    parent: 'DAEGU',
    name: '달서구',
  },
  {
    code: 'DAEGU_DALSEONG',
    parent: 'DAEGU',
    name: '달성군',
  },
  {
    code: 'DAEGU_GUNWI',
    parent: 'DAEGU',
    name: '군위군',
  },
  {
    code: 'INCHEON_JUNG',
    parent: 'INCHEON',
    name: '중구',
  },
  {
    code: 'INCHEON_DONG',
    parent: 'INCHEON',
    name: '동구',
  },
  {
    code: 'INCHEON_MICHUHOL',
    parent: 'INCHEON',
    name: '미추홀구',
  },
  {
    code: 'INCHEON_YEONSU',
    parent: 'INCHEON',
    name: '연수구',
  },
  {
    code: 'INCHEON_NAMDONG',
    parent: 'INCHEON',
    name: '남동구',
  },
  {
    code: 'INCHEON_BUPYEONG',
    parent: 'INCHEON',
    name: '부평구',
  },
  {
    code: 'INCHEON_GYEYANG',
    parent: 'INCHEON',
    name: '계양구',
  },
  {
    code: 'INCHEON_SEO',
    parent: 'INCHEON',
    name: '서구',
  },
  {
    code: 'INCHEON_GANGHWA',
    parent: 'INCHEON',
    name: '강화군',
  },
  {
    code: 'INCHEON_ONGJIN',
    parent: 'INCHEON',
    name: '옹진군',
  },
  {
    code: 'GWANGJU_DONG',
    parent: 'GWANGJU',
    name: '동구',
  },
  {
    code: 'GWANGJU_SEO',
    parent: 'GWANGJU',
    name: '서구',
  },
  {
    code: 'GWANGJU_NAM',
    parent: 'GWANGJU',
    name: '남구',
  },
  {
    code: 'GWANGJU_BUK',
    parent: 'GWANGJU',
    name: '북구',
  },
  {
    code: 'GWANGJU_GWANGSAN',
    parent: 'GWANGJU',
    name: '광산구',
  },
  {
    code: 'DAEJEON_DONG',
    parent: 'DAEJEON',
    name: '동구',
  },
  {
    code: 'DAEJEON_JUNG',
    parent: 'DAEJEON',
    name: '중구',
  },
  {
    code: 'DAEJEON_SEO',
    parent: 'DAEJEON',
    name: '서구',
  },
  {
    code: 'DAEJEON_YUSEONG',
    parent: 'DAEJEON',
    name: '유성구',
  },
  {
    code: 'DAEJEON_DAEDEOK',
    parent: 'DAEJEON',
    name: '대덕구',
  },
  {
    code: 'ULSAN_JUNG',
    parent: 'ULSAN',
    name: '중구',
  },
  {
    code: 'ULSAN_NAM',
    parent: 'ULSAN',
    name: '남구',
  },
  {
    code: 'ULSAN_DONG',
    parent: 'ULSAN',
    name: '동구',
  },
  {
    code: 'ULSAN_BUK',
    parent: 'ULSAN',
    name: '북구',
  },
  {
    code: 'ULSAN_ULJU',
    parent: 'ULSAN',
    name: '울주군',
  },
  {
    code: 'SEJONG_SEJONG',
    parent: 'SEJONG',
    name: '세종특별자치시',
  },
  {
    code: 'GYEONGGI_SUWON',
    parent: 'GYEONGGI',
    name: '수원시',
  },
  {
    code: 'GYEONGGI_SEONGNAM',
    parent: 'GYEONGGI',
    name: '성남시',
  },
  {
    code: 'GYEONGGI_UIJEONGBU',
    parent: 'GYEONGGI',
    name: '의정부시',
  },
  {
    code: 'GYEONGGI_ANYANG',
    parent: 'GYEONGGI',
    name: '안양시',
  },
  {
    code: 'GYEONGGI_BUCHEON',
    parent: 'GYEONGGI',
    name: '부천시',
  },
  {
    code: 'GYEONGGI_GWANGMYEONG',
    parent: 'GYEONGGI',
    name: '광명시',
  },
  {
    code: 'GYEONGGI_PYEONGTAEK',
    parent: 'GYEONGGI',
    name: '평택시',
  },
  {
    code: 'GYEONGGI_DONGDUCHEON',
    parent: 'GYEONGGI',
    name: '동두천시',
  },
  {
    code: 'GYEONGGI_ANSAN',
    parent: 'GYEONGGI',
    name: '안산시',
  },
  {
    code: 'GYEONGGI_GOYANG',
    parent: 'GYEONGGI',
    name: '고양시',
  },
  {
    code: 'GYEONGGI_GWACHEON',
    parent: 'GYEONGGI',
    name: '과천시',
  },
  {
    code: 'GYEONGGI_GURI',
    parent: 'GYEONGGI',
    name: '구리시',
  },
  {
    code: 'GYEONGGI_NAMYANGJU',
    parent: 'GYEONGGI',
    name: '남양주시',
  },
  {
    code: 'GYEONGGI_OSAN',
    parent: 'GYEONGGI',
    name: '오산시',
  },
  {
    code: 'GYEONGGI_SIHEUNG',
    parent: 'GYEONGGI',
    name: '시흥시',
  },
  {
    code: 'GYEONGGI_GUNPO',
    parent: 'GYEONGGI',
    name: '군포시',
  },
  {
    code: 'GYEONGGI_UIWANG',
    parent: 'GYEONGGI',
    name: '의왕시',
  },
  {
    code: 'GYEONGGI_HANAM',
    parent: 'GYEONGGI',
    name: '하남시',
  },
  {
    code: 'GYEONGGI_YONGIN',
    parent: 'GYEONGGI',
    name: '용인시',
  },
  {
    code: 'GYEONGGI_PAJU',
    parent: 'GYEONGGI',
    name: '파주시',
  },
  {
    code: 'GYEONGGI_ICHEON',
    parent: 'GYEONGGI',
    name: '이천시',
  },
  {
    code: 'GYEONGGI_ANSEONG',
    parent: 'GYEONGGI',
    name: '안성시',
  },
  {
    code: 'GYEONGGI_GIMPO',
    parent: 'GYEONGGI',
    name: '김포시',
  },
  {
    code: 'GYEONGGI_HWASEONG',
    parent: 'GYEONGGI',
    name: '화성시',
  },
  {
    code: 'GYEONGGI_GWANGJU',
    parent: 'GYEONGGI',
    name: '광주시',
  },
  {
    code: 'GYEONGGI_YANGJU',
    parent: 'GYEONGGI',
    name: '양주시',
  },
  {
    code: 'GYEONGGI_POCHEON',
    parent: 'GYEONGGI',
    name: '포천시',
  },
  {
    code: 'GYEONGGI_YEOJU',
    parent: 'GYEONGGI',
    name: '여주시',
  },
  {
    code: 'GYEONGGI_YEONCHEON',
    parent: 'GYEONGGI',
    name: '연천군',
  },
  {
    code: 'GYEONGGI_GAPYEONG',
    parent: 'GYEONGGI',
    name: '가평군',
  },
  {
    code: 'GYEONGGI_YANGPYEONG',
    parent: 'GYEONGGI',
    name: '양평군',
  },
  {
    code: 'GANGWON_CHUNCHEON',
    parent: 'GANGWON',
    name: '춘천시',
  },
  {
    code: 'GANGWON_WONJU',
    parent: 'GANGWON',
    name: '원주시',
  },
  {
    code: 'GANGWON_GANGNEUNG',
    parent: 'GANGWON',
    name: '강릉시',
  },
  {
    code: 'GANGWON_DONGHAE',
    parent: 'GANGWON',
    name: '동해시',
  },
  {
    code: 'GANGWON_TAEBAEK',
    parent: 'GANGWON',
    name: '태백시',
  },
  {
    code: 'GANGWON_SOKCHO',
    parent: 'GANGWON',
    name: '속초시',
  },
  {
    code: 'GANGWON_SAMCHEOK',
    parent: 'GANGWON',
    name: '삼척시',
  },
  {
    code: 'GANGWON_HONGCHEON',
    parent: 'GANGWON',
    name: '홍천군',
  },
  {
    code: 'GANGWON_HOENGSEONG',
    parent: 'GANGWON',
    name: '횡성군',
  },
  {
    code: 'GANGWON_YEONGWOL',
    parent: 'GANGWON',
    name: '영월군',
  },
  {
    code: 'GANGWON_PYEONGCHANG',
    parent: 'GANGWON',
    name: '평창군',
  },
  {
    code: 'GANGWON_JEONGSEON',
    parent: 'GANGWON',
    name: '정선군',
  },
  {
    code: 'GANGWON_CHEORWON',
    parent: 'GANGWON',
    name: '철원군',
  },
  {
    code: 'GANGWON_HWACHEON',
    parent: 'GANGWON',
    name: '화천군',
  },
  {
    code: 'GANGWON_YANGGU',
    parent: 'GANGWON',
    name: '양구군',
  },
  {
    code: 'GANGWON_INJE',
    parent: 'GANGWON',
    name: '인제군',
  },
  {
    code: 'GANGWON_GOSEONG',
    parent: 'GANGWON',
    name: '고성군',
  },
  {
    code: 'GANGWON_YANGYANG',
    parent: 'GANGWON',
    name: '양양군',
  },
  {
    code: 'CHUNGBUK_CHEONGJU',
    parent: 'CHUNGBUK',
    name: '청주시',
  },
  {
    code: 'CHUNGBUK_CHUNGJU',
    parent: 'CHUNGBUK',
    name: '충주시',
  },
  {
    code: 'CHUNGBUK_JECHEON',
    parent: 'CHUNGBUK',
    name: '제천시',
  },
  {
    code: 'CHUNGBUK_BOEUN',
    parent: 'CHUNGBUK',
    name: '보은군',
  },
  {
    code: 'CHUNGBUK_OKCHEON',
    parent: 'CHUNGBUK',
    name: '옥천군',
  },
  {
    code: 'CHUNGBUK_YEONGDONG',
    parent: 'CHUNGBUK',
    name: '영동군',
  },
  {
    code: 'CHUNGBUK_JEUNGPYEONG',
    parent: 'CHUNGBUK',
    name: '증평군',
  },
  {
    code: 'CHUNGBUK_JINCHEON',
    parent: 'CHUNGBUK',
    name: '진천군',
  },
  {
    code: 'CHUNGBUK_GOESAN',
    parent: 'CHUNGBUK',
    name: '괴산군',
  },
  {
    code: 'CHUNGBUK_EUMSEONG',
    parent: 'CHUNGBUK',
    name: '음성군',
  },
  {
    code: 'CHUNGBUK_DANYANG',
    parent: 'CHUNGBUK',
    name: '단양군',
  },
  {
    code: 'CHUNGNAM_CHEONAN',
    parent: 'CHUNGNAM',
    name: '천안시',
  },
  {
    code: 'CHUNGNAM_GONGJU',
    parent: 'CHUNGNAM',
    name: '공주시',
  },
  {
    code: 'CHUNGNAM_BORYEONG',
    parent: 'CHUNGNAM',
    name: '보령시',
  },
  {
    code: 'CHUNGNAM_ASAN',
    parent: 'CHUNGNAM',
    name: '아산시',
  },
  {
    code: 'CHUNGNAM_SEOSAN',
    parent: 'CHUNGNAM',
    name: '서산시',
  },
  {
    code: 'CHUNGNAM_NONSAN',
    parent: 'CHUNGNAM',
    name: '논산시',
  },
  {
    code: 'CHUNGNAM_GYERYONG',
    parent: 'CHUNGNAM',
    name: '계룡시',
  },
  {
    code: 'CHUNGNAM_DANGJIN',
    parent: 'CHUNGNAM',
    name: '당진시',
  },
  {
    code: 'CHUNGNAM_GEUMSAN',
    parent: 'CHUNGNAM',
    name: '금산군',
  },
  {
    code: 'CHUNGNAM_BUYEO',
    parent: 'CHUNGNAM',
    name: '부여군',
  },
  {
    code: 'CHUNGNAM_SEOCHEON',
    parent: 'CHUNGNAM',
    name: '서천군',
  },
  {
    code: 'CHUNGNAM_CHEONGYANG',
    parent: 'CHUNGNAM',
    name: '청양군',
  },
  {
    code: 'CHUNGNAM_HONGSEONG',
    parent: 'CHUNGNAM',
    name: '홍성군',
  },
  {
    code: 'CHUNGNAM_YESAN',
    parent: 'CHUNGNAM',
    name: '예산군',
  },
  {
    code: 'CHUNGNAM_TAEAN',
    parent: 'CHUNGNAM',
    name: '태안군',
  },
  {
    code: 'JEONBUK_JEONJU',
    parent: 'JEONBUK',
    name: '전주시',
  },
  {
    code: 'JEONBUK_GUNSAN',
    parent: 'JEONBUK',
    name: '군산시',
  },
  {
    code: 'JEONBUK_IKSAN',
    parent: 'JEONBUK',
    name: '익산시',
  },
  {
    code: 'JEONBUK_JEONGEUP',
    parent: 'JEONBUK',
    name: '정읍시',
  },
  {
    code: 'JEONBUK_NAMWON',
    parent: 'JEONBUK',
    name: '남원시',
  },
  {
    code: 'JEONBUK_GIMJE',
    parent: 'JEONBUK',
    name: '김제시',
  },
  {
    code: 'JEONBUK_WANJU',
    parent: 'JEONBUK',
    name: '완주군',
  },
  {
    code: 'JEONBUK_JINAN',
    parent: 'JEONBUK',
    name: '진안군',
  },
  {
    code: 'JEONBUK_MUJU',
    parent: 'JEONBUK',
    name: '무주군',
  },
  {
    code: 'JEONBUK_JANGSU',
    parent: 'JEONBUK',
    name: '장수군',
  },
  {
    code: 'JEONBUK_IMSIL',
    parent: 'JEONBUK',
    name: '임실군',
  },
  {
    code: 'JEONBUK_SUNCHANG',
    parent: 'JEONBUK',
    name: '순창군',
  },
  {
    code: 'JEONBUK_GOCHANG',
    parent: 'JEONBUK',
    name: '고창군',
  },
  {
    code: 'JEONBUK_BUAN',
    parent: 'JEONBUK',
    name: '부안군',
  },
  {
    code: 'JEONNAM_MOKPO',
    parent: 'JEONNAM',
    name: '목포시',
  },
  {
    code: 'JEONNAM_YEOSU',
    parent: 'JEONNAM',
    name: '여수시',
  },
  {
    code: 'JEONNAM_SUNCHEON',
    parent: 'JEONNAM',
    name: '순천시',
  },
  {
    code: 'JEONNAM_NAJU',
    parent: 'JEONNAM',
    name: '나주시',
  },
  {
    code: 'JEONNAM_GWANGYANG',
    parent: 'JEONNAM',
    name: '광양시',
  },
  {
    code: 'JEONNAM_DAMYANG',
    parent: 'JEONNAM',
    name: '담양군',
  },
  {
    code: 'JEONNAM_GOKSEONG',
    parent: 'JEONNAM',
    name: '곡성군',
  },
  {
    code: 'JEONNAM_GURYE',
    parent: 'JEONNAM',
    name: '구례군',
  },
  {
    code: 'JEONNAM_GOHEUNG',
    parent: 'JEONNAM',
    name: '고흥군',
  },
  {
    code: 'JEONNAM_BOSEONG',
    parent: 'JEONNAM',
    name: '보성군',
  },
  {
    code: 'JEONNAM_HWASUN',
    parent: 'JEONNAM',
    name: '화순군',
  },
  {
    code: 'JEONNAM_JANGHEUNG',
    parent: 'JEONNAM',
    name: '장흥군',
  },
  {
    code: 'JEONNAM_GANGJIN',
    parent: 'JEONNAM',
    name: '강진군',
  },
  {
    code: 'JEONNAM_HAENAM',
    parent: 'JEONNAM',
    name: '해남군',
  },
  {
    code: 'JEONNAM_YEONGAM',
    parent: 'JEONNAM',
    name: '영암군',
  },
  {
    code: 'JEONNAM_MUAN',
    parent: 'JEONNAM',
    name: '무안군',
  },
  {
    code: 'JEONNAM_HAMPYEONG',
    parent: 'JEONNAM',
    name: '함평군',
  },
  {
    code: 'JEONNAM_YEONGGWANG',
    parent: 'JEONNAM',
    name: '영광군',
  },
  {
    code: 'JEONNAM_JANGSEONG',
    parent: 'JEONNAM',
    name: '장성군',
  },
  {
    code: 'JEONNAM_WANDO',
    parent: 'JEONNAM',
    name: '완도군',
  },
  {
    code: 'JEONNAM_JINDO',
    parent: 'JEONNAM',
    name: '진도군',
  },
  {
    code: 'JEONNAM_SINAN',
    parent: 'JEONNAM',
    name: '신안군',
  },
  {
    code: 'GYEONGBUK_POHANG',
    parent: 'GYEONGBUK',
    name: '포항시',
  },
  {
    code: 'GYEONGBUK_GYEONGJU',
    parent: 'GYEONGBUK',
    name: '경주시',
  },
  {
    code: 'GYEONGBUK_GIMCHEON',
    parent: 'GYEONGBUK',
    name: '김천시',
  },
  {
    code: 'GYEONGBUK_ANDONG',
    parent: 'GYEONGBUK',
    name: '안동시',
  },
  {
    code: 'GYEONGBUK_GUMI',
    parent: 'GYEONGBUK',
    name: '구미시',
  },
  {
    code: 'GYEONGBUK_YEONGJU',
    parent: 'GYEONGBUK',
    name: '영주시',
  },
  {
    code: 'GYEONGBUK_YEONGCHEON',
    parent: 'GYEONGBUK',
    name: '영천시',
  },
  {
    code: 'GYEONGBUK_SANGJU',
    parent: 'GYEONGBUK',
    name: '상주시',
  },
  {
    code: 'GYEONGBUK_MUNGYEONG',
    parent: 'GYEONGBUK',
    name: '문경시',
  },
  {
    code: 'GYEONGBUK_GYEONGSAN',
    parent: 'GYEONGBUK',
    name: '경산시',
  },
  {
    code: 'GYEONGBUK_UISEONG',
    parent: 'GYEONGBUK',
    name: '의성군',
  },
  {
    code: 'GYEONGBUK_CHEONGSONG',
    parent: 'GYEONGBUK',
    name: '청송군',
  },
  {
    code: 'GYEONGBUK_YEONGYANG',
    parent: 'GYEONGBUK',
    name: '영양군',
  },
  {
    code: 'GYEONGBUK_YEONGDEOK',
    parent: 'GYEONGBUK',
    name: '영덕군',
  },
  {
    code: 'GYEONGBUK_CHEONGDO',
    parent: 'GYEONGBUK',
    name: '청도군',
  },
  {
    code: 'GYEONGBUK_GORYEONG',
    parent: 'GYEONGBUK',
    name: '고령군',
  },
  {
    code: 'GYEONGBUK_SEONGJU',
    parent: 'GYEONGBUK',
    name: '성주군',
  },
  {
    code: 'GYEONGBUK_CHILGOK',
    parent: 'GYEONGBUK',
    name: '칠곡군',
  },
  {
    code: 'GYEONGBUK_YECHEON',
    parent: 'GYEONGBUK',
    name: '예천군',
  },
  {
    code: 'GYEONGBUK_BONGHWA',
    parent: 'GYEONGBUK',
    name: '봉화군',
  },
  {
    code: 'GYEONGBUK_ULJIN',
    parent: 'GYEONGBUK',
    name: '울진군',
  },
  {
    code: 'GYEONGBUK_ULLEUNG',
    parent: 'GYEONGBUK',
    name: '울릉군',
  },
  {
    code: 'GYEONGNAM_CHANGWON',
    parent: 'GYEONGNAM',
    name: '창원시',
  },
  {
    code: 'GYEONGNAM_JINJU',
    parent: 'GYEONGNAM',
    name: '진주시',
  },
  {
    code: 'GYEONGNAM_TONGYEONG',
    parent: 'GYEONGNAM',
    name: '통영시',
  },
  {
    code: 'GYEONGNAM_SACHEON',
    parent: 'GYEONGNAM',
    name: '사천시',
  },
  {
    code: 'GYEONGNAM_GIMHAE',
    parent: 'GYEONGNAM',
    name: '김해시',
  },
  {
    code: 'GYEONGNAM_MIRYANG',
    parent: 'GYEONGNAM',
    name: '밀양시',
  },
  {
    code: 'GYEONGNAM_GEOJE',
    parent: 'GYEONGNAM',
    name: '거제시',
  },
  {
    code: 'GYEONGNAM_YANGSAN',
    parent: 'GYEONGNAM',
    name: '양산시',
  },
  {
    code: 'GYEONGNAM_UIRYEONG',
    parent: 'GYEONGNAM',
    name: '의령군',
  },
  {
    code: 'GYEONGNAM_HAMAN',
    parent: 'GYEONGNAM',
    name: '함안군',
  },
  {
    code: 'GYEONGNAM_CHANGNYEONG',
    parent: 'GYEONGNAM',
    name: '창녕군',
  },
  {
    code: 'GYEONGNAM_GOSEONG',
    parent: 'GYEONGNAM',
    name: '고성군',
  },
  {
    code: 'GYEONGNAM_NAMHAE',
    parent: 'GYEONGNAM',
    name: '남해군',
  },
  {
    code: 'GYEONGNAM_HADONG',
    parent: 'GYEONGNAM',
    name: '하동군',
  },
  {
    code: 'GYEONGNAM_SANCHEONG',
    parent: 'GYEONGNAM',
    name: '산청군',
  },
  {
    code: 'GYEONGNAM_HAMYANG',
    parent: 'GYEONGNAM',
    name: '함양군',
  },
  {
    code: 'GYEONGNAM_GEOCHANG',
    parent: 'GYEONGNAM',
    name: '거창군',
  },
  {
    code: 'GYEONGNAM_HAPCHEON',
    parent: 'GYEONGNAM',
    name: '합천군',
  },
  {
    code: 'JEJU_JEJU',
    parent: 'JEJU',
    name: '제주시',
  },
  {
    code: 'JEJU_SEOGWIPO',
    parent: 'JEJU',
    name: '서귀포시',
  },
];
