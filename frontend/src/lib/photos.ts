// 화면에 쓰는 사진과 출처. 모두 Wikimedia Commons 사진이고, 화면에 필요한 크기(레티나 2배)로 줄여서 쓴다.
// CC BY, CC BY-SA는 저작자와 라이선스를 밝히면 쓸 수 있다. 출처 페이지(/credits)와 사진 아래 글에 같이 적는다.

export interface Photo {
  src: string;
  alt: string;
  title: string;
  author: string;
  license: string;
  licenseUrl: string;
  /** Commons의 원본 파일 페이지 */
  sourceUrl: string;
}

const BY_SA_4 = { license: "CC BY-SA 4.0", licenseUrl: "https://creativecommons.org/licenses/by-sa/4.0/deed.ko" };

export const PHOTOS = {
  umeda: {
    src: "/img/umeda.jpg",
    alt: "우메다 스카이 빌딩의 에스컬레이터 터널",
    title: "우메다 스카이 빌딩",
    author: "Martin Falbisoner",
    ...BY_SA_4,
    sourceUrl: "https://commons.wikimedia.org/wiki/File:Umeda_Sky_Building,_Osaka,_November_2016_-02.jpg",
  },
  kuromon: {
    src: "/img/kuromon.jpg",
    alt: "구로몬 시장 입구와 거리",
    title: "구로몬 시장",
    author: "Mr.ちゅらさん",
    ...BY_SA_4,
    sourceUrl: "https://commons.wikimedia.org/wiki/File:Osaka_Kuromon_Ichiba_Market_2017-12_(1).jpg",
  },
  castle: {
    src: "/img/castle.jpg",
    alt: "나무 사이로 보이는 오사카성 천수각",
    title: "오사카성 천수각",
    author: "DXR",
    ...BY_SA_4,
    sourceUrl: "https://commons.wikimedia.org/wiki/File:Osaka_Castle,_Keep_tower,_South_view_20190415_1.jpg",
  },
} satisfies Record<string, Photo>;

export type PhotoKey = keyof typeof PHOTOS;
