# 오늘의 급식

NEIS 오픈 API에서 오늘 급식(조식·중식·석식)을 받아와 이미지에 그린 뒤, Windows 바탕화면으로 자동 설정하는 스크립트입니다.

![예시](example.png)

## 동작 방식

1. `datetime`으로 오늘 날짜(`YYYYMMDD`)를 구합니다.
2. NEIS `mealServiceDietInfo` API를 조식(`1`)·중식(`2`)·석식(`3`)으로 각각 호출합니다.
3. `base.png` 위에 Pillow로 급식과 날짜를 순서대로 그립니다.
4. 중간 이미지는 삭제하고, 최종 `YYYYMMDD.png`를 `SystemParametersInfoW(20, ...)`로 바탕화면에 적용합니다.

## 요구 사항

- Windows (바탕화면 설정에 `user32.dll`, 폰트에 `C:\Windows\Fonts\malgunbd.ttf`를 사용합니다)
- Python 3
- `base.png` (배경으로 쓸 이미지, 저장소에 포함)

## 설치

```bash
pip install -r requirements.txt
```

의존성: `requests`, `Pillow`

## 실행

```bash
python main.pyw
```

`.pyw` 확장자이므로 더블 클릭하면 콘솔 창 없이 실행됩니다. 매일 자동 실행하려면 작업 스케줄러에 등록하세요.

## 다른 학교로 바꾸기

`main.pyw`의 `niesAPI` 클래스 값을 수정합니다.

```python
class niesAPI:
    ATPT_OFCDC_SC_CODE = "I10";   # 시도교육청 코드
    SD_SCHUL_CODE = "9300058";    # 표준학교 코드
```

코드 값은 NEIS 오픈 API의 `schoolInfo` 엔드포인트에서 학교명으로 조회할 수 있습니다.

## 파일 구조

| 파일 | 설명 |
| --- | --- |
| `main.pyw` | 전체 로직 (API 호출 · 이미지 생성 · 바탕화면 설정) |
| `base.png` | 급식을 그려 넣을 배경 이미지 |
| `requirements.txt` | 의존성 목록 |
| `YYYYMMDD.png` | 실행 결과로 생성되는 바탕화면 이미지 |
