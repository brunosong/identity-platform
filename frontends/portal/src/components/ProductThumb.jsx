/**
 * 상품 타일.
 *
 * 상품 사진이 없다. 빈 회색 칸을 여덟 개 늘어놓으면 화면이 읽히지 않아서, 상품마다 다른 색을
 * 깔고 이름 첫 글자를 얹는다. 사진이 생기면 이 파일만 img 로 바꾼다.
 */
export default function ProductThumb({ product, size = 'card' }) {
    const { tone } = product;
    const background = `linear-gradient(140deg,
        hsl(${tone} 42% 24%) 0%,
        hsl(${tone + 24} 38% 15%) 100%)`;

    return (
        <div className={`thumb thumb-${size}`} style={{ background }} aria-hidden="true">
            <span style={{ color: `hsl(${tone} 70% 72%)` }}>{product.name.slice(0, 1)}</span>
        </div>
    );
}
