// 화면에 쓰는 사진과 출처. 모두 Wikimedia Commons 사진이고 크기를 줄여서 쓴다.
// CC BY, CC BY-SA는 저작자와 라이선스를 밝히면 쓸 수 있다. 출처 페이지(/credits)와 사진 아래 글에 같이 적는다.

export interface Photo {
  src: string;
  alt: string;
  title: string;
  author: string;
  license: string;
  licenseUrl: string;
}

const BY_SA_4 = { license: "CC BY-SA 4.0", licenseUrl: "https://creativecommons.org/licenses/by-sa/4.0/deed.ko" };
const BY_2 = { license: "CC BY 2.0", licenseUrl: "https://creativecommons.org/licenses/by/2.0/deed.ko" };

export const PHOTOS = {
  umeda: { src: "/img/umeda.jpg", alt: "우메다 공중정원 빌딩", title: "우메다 공중정원", author: "Martin Falbisoner", ...BY_SA_4 },
  kuromon: { src: "/img/kuromon.jpg", alt: "구로몬 시장 거리", title: "구로몬 시장", author: "Mr.ちゅらさん", ...BY_SA_4 },
  castle: { src: "/img/castle.jpg", alt: "오사카성", title: "오사카성", author: "alisdair", ...BY_2 },
} satisfies Record<string, Photo>;

export type PhotoKey = keyof typeof PHOTOS;
