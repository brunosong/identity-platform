import { fetchProducts } from '../api/order';

/**
 * 상품 목록.
 *
 * 값이 두 곳에서 온다. 이름, 가격, 남은 수량은 order-service 가 준다. 주문할 때 서버가 그 값으로
 * 판단하므로 화면도 같은 값을 보여야 한다. 설명, 사양, 색은 화면에만 쓰는 값이라 여기 둔다.
 *
 * <b>화면은 이 파일을 직접 읽지 않는다.</b> 아래 두 함수만 부른다.
 *
 * tone 은 색상환의 각도다. 상품 사진이 없어서 이 값으로 타일 색을 만든다.
 */

const DISPLAY = [
    {
        id: 'kb-realforce-45',
        category: '주변기기',
        tone: 222,
        summary: '누르는 깊이로 입력을 잡는 방식이라 끝까지 누르지 않아도 된다.',
        description: [
            '접점이 닿아서 입력되는 것이 아니라 눌린 깊이를 재서 입력한다. 긁히는 부품이 없어 수명이 길고, 바닥을 치지 않아도 되니 손목에 덜 남는다.',
            '45g 은 하루 종일 치는 사람이 가장 많이 고르는 무게다. 더 가벼우면 오타가 늘고, 더 무거우면 저녁에 손가락이 뻐근하다.',
        ],
        specs: {
            '스위치': '정전용량 무접점 45g',
            '배열': '텐키리스 87키',
            '연결': 'USB-C 유선',
            '키캡': 'PBT 승화 인쇄',
            '무게': '1.1kg',
        },
    },
    {
        id: 'mn-lg-27-4k',
        category: '모니터',
        tone: 265,
        summary: '글자를 오래 보는 사람을 위한 해상도. 27인치에서 4K 는 인쇄물에 가깝다.',
        description: [
            '같은 4K 라도 32인치에서는 화면이 넓어지고 27인치에서는 글자가 선명해진다. 코드를 읽는 자리라면 후자가 낫다.',
            '색은 sRGB 99% 로 맞춰 나온다. 사진 작업까지 할 생각이라면 Adobe RGB 를 덮는 모델을 따로 봐야 한다.',
        ],
        specs: {
            '크기': '27인치 IPS',
            '해상도': '3840 x 2160',
            '주사율': '60Hz',
            '색영역': 'sRGB 99%',
            '입력': 'HDMI 2.0 x2, DP 1.4, USB-C 65W',
        },
    },
    {
        id: 'hp-sony-wh',
        category: '음향',
        tone: 190,
        summary: '지하철과 카페의 웅웅거림이 사라진다. 사람 목소리는 남는다.',
        description: [
            '낮은 소리를 지우는 데 강하다. 엔진음, 에어컨, 지하철 소음처럼 일정하게 깔리는 소리가 대상이다.',
            '옆자리 대화는 생각보다 덜 지워진다. 그쪽을 지우려면 소리를 덮는 수밖에 없고, 그건 귀에 좋지 않다.',
        ],
        specs: {
            '형태': '오버이어 밀폐형',
            '재생시간': '30시간 (ANC 켜고)',
            '충전': 'USB-C, 3분 충전 3시간 재생',
            '코덱': 'SBC, AAC, LDAC',
            '무게': '250g',
        },
    },
    {
        id: 'ms-mx-master',
        category: '주변기기',
        tone: 152,
        summary: '손목을 비틀지 않는 각도. 익숙해지는 데 사흘쯤 걸린다.',
        description: [
            '일반 마우스는 손바닥을 바닥으로 엎는 자세를 만든다. 이 각도는 악수하는 자세에 가까워서 아래팔이 꼬이지 않는다.',
            '처음 며칠은 오히려 불편하다. 정확도가 떨어지는 기간을 지나야 효과가 나오므로, 마감 직전에 바꾸지 않는 편이 좋다.',
        ],
        specs: {
            '센서': '4000 DPI',
            '연결': '블루투스, 전용 수신기',
            '배터리': '70일',
            '버튼': '6개 (전부 재지정 가능)',
            '지원': '오른손 전용',
        },
    },
    {
        id: 'nb-a5-dot',
        category: '문구',
        tone: 36,
        summary: '줄도 격자도 아닌 점. 그려도 되고 써도 된다.',
        description: [
            '줄노트는 글씨를 강제하고 격자는 그림을 강제한다. 점은 둘 다 되게 두고 방해하지 않는다.',
            '종이가 100g 이라 만년필로 써도 뒤에 비치지 않는다. 얇은 노트에 만년필을 쓰다 포기한 적이 있다면 차이가 크다.',
        ],
        specs: {
            '크기': 'A5 (148 x 210mm)',
            '내지': '도트 5mm, 100g 무형광',
            '쪽수': '권당 192쪽',
            '제본': '실제본 (펼쳐 놓으면 눕는다)',
            '구성': '3권 묶음',
        },
    },
    {
        id: 'lp-stand-al',
        category: '데스크',
        tone: 205,
        summary: '화면을 눈높이로 올린다. 목이 앞으로 나가지 않게 하는 가장 싼 방법.',
        description: [
            '노트북을 책상에 그대로 두면 화면이 눈보다 한참 아래에 있고, 그걸 보려면 목이 앞으로 나간다. 하루 여덟 시간이면 목에 남는다.',
            '대신 키보드를 따로 써야 한다. 거치대를 사면서 키보드를 안 사면 이번에는 어깨가 올라간다.',
        ],
        specs: {
            '재질': '알루미늄 단조',
            '높이': '6단 (최대 155mm)',
            '지원 크기': '11 ~ 16인치',
            '하중': '10kg',
            '접이': '가능 (두께 28mm)',
        },
    },
    {
        id: 'cb-usbc-240w',
        category: '케이블',
        tone: 285,
        summary: '생긴 건 다 같아 보이지만 통하는 전력이 다르다.',
        description: [
            'USB-C 는 모양만 같고 안이 다르다. 60W 케이블에 노트북을 물리면 충전이 되다 말거나 아주 느리다.',
            '240W 는 현재 규격의 위쪽이다. 노트북, 모니터, 휴대폰을 한 종류로 통일하고 싶을 때 고르는 값이다.',
        ],
        specs: {
            '전력': '240W (48V 5A)',
            '데이터': 'USB 2.0 480Mbps',
            '길이': '2m',
            '피복': '나일론 편조',
            '인증': 'USB-IF 정식 인증',
        },
    },
    {
        id: 'mg-ceramic-350',
        category: '주방',
        tone: 18,
        summary: '두꺼워서 늦게 식는다. 오래 앉아 있는 사람에게 맞다.',
        description: [
            '얇은 잔은 향이 잘 올라오지만 십 분이면 식는다. 이건 반대다. 한 시간 뒤에도 미지근한 정도로 남는다.',
            '전자레인지와 식기세척기를 모두 견딘다. 다만 바닥 유약이 없는 부분이 있어 물기를 남기면 자국이 진다.',
        ],
        specs: {
            '용량': '350ml',
            '재질': '석기질 도자기',
            '두께': '6mm',
            '사용': '전자레인지, 식기세척기 가능',
            '무게': '420g',
        },
    },
];

/** 서버에는 있는데 화면 정보가 아직 없는 상품. 이름과 가격만으로 보인다. */
const NO_DISPLAY = { category: '', tone: 220, summary: '', description: [], specs: {} };

/**
 * 목록 화면이 부른다. 서버가 아는 상품만 나온다. 판매 여부는 서버가 정한다.
 *
 * @returns {{ok, status, message, products}}
 */
export async function listProducts() {
    const result = await fetchProducts();
    if (!result.ok) {
        return { ...result, products: [] };
    }

    const products = result.data.map(withDisplay);
    return { ...result, products };
}

/**
 * 상세 화면이 부른다. 없는 id 면 product 가 undefined 다. 화면이 그 경우를 처리한다.
 *
 * 상품 하나만 주는 API 가 없어서 목록에서 찾는다. 상품이 여덟 개라 그것으로 충분하다.
 */
export async function findProduct(id) {
    const result = await listProducts();
    const product = result.products.find((candidate) => candidate.id === id);
    return { ...result, product };
}

function withDisplay(server) {
    const display = DISPLAY.find((candidate) => candidate.id === server.productId) ?? NO_DISPLAY;
    return {
        ...display,
        id: server.productId,
        name: server.name,
        price: server.price,
        stock: server.stock,
    };
}

/** 화면 여러 곳에서 같은 모양으로 찍어야 해서 여기 둔다. */
export function formatPrice(price) {
    return `${price.toLocaleString('ko-KR')}원`;
}
