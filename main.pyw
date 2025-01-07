# .pyw 확장자
import requests;
from concurrent.futures import ThreadPoolExecutor;
from PIL import Image, ImageDraw, ImageFont;
import ctypes;
import os;
from datetime import datetime;


class niesAPI:
    ATPT_OFCDC_SC_CODE = "I10";
    SD_SCHUL_CODE = "9300058";


def getCurrentDate() -> str:
    date = datetime.now();
    formattedDate = date.strftime("%Y%m%d");

    return formattedDate;


def getMeal(date: str, mealScCode: str, type: str) -> str:
    request_url = (
            "https://open.neis.go.kr/hub/mealServiceDietInfo?"
            "&Type=" + "json" +
            "&MMEAL_SC_CODE=" + mealScCode +
            "&ATPT_OFCDC_SC_CODE=" + niesAPI.ATPT_OFCDC_SC_CODE +
            "&SD_SCHUL_CODE=" + niesAPI.SD_SCHUL_CODE +
            "&MLSV_YMD=" + date
    );

    with ThreadPoolExecutor(max_workers=1) as executor:
        future_result = executor.submit(parseMealData, request_url, type);
        return future_result.result();


def getTimetable(date: str) -> str:
    return "시간표";
    # url = (
    #         "https://open.neis.go.kr/hub/hisTimetable?"
    #         "&Type=" + "json" +
    #         "&ATPT_OFCDC_SC_CODE=" + niesAPI.ATPT_OFCDC_SC_CODE +
    #         "&SD_SCHUL_CODE=" + niesAPI.SD_SCHUL_CODE +
    #         "&ALL_TI_YMD=" + date +
    #         "&GRADE=" + f"{2}" +
    #         "&CLASS_NM=" + f"{1}"
    # );
    #
    # with ThreadPoolExecutor(max_workers=1) as executor:
    #     future_result = executor.submit(parseTimetableData, url);
    #     return future_result.result();


def parseTimetableData(url: str) -> str:
    try :
        response = requests.get(url, headers={"Content-Type": "application/json"});
        response_data = response.json();
        timetable_array = response_data["hisTimetable"][1]["row"];
        result = "";

        for item in timetable_array:
            PERIO = item["PERIO"];
            ITRT_CNTNT = item["ITRT_CNTNT"];
            result += f"{PERIO}교시: {ITRT_CNTNT}\n";

    except Exception as e:
        print(f"return Error: {e}");
        return e;

    return result;


def parseMealData(url: str, type: str) -> str:
    try:
        response = requests.get(url, headers={"Content-Type": "application/json"});
        response_data = response.json();
        timetable_array = response_data["mealServiceDietInfo"][1]["row"];

        for item in timetable_array:
            menu = item["DDISH_NM"];
            calories = item["CAL_INFO"];
            nutrition_info = item["NTR_INFO"];

            if type == "메뉴":
                return menu.replace("<br/>", "\n");
            elif type == "칼로리":
                return calories.replace("<br/>", "\n");
            elif type == "영양정보":
                return nutrition_info.replace("<br/>", "\n");
    except Exception as e:
        print(f"return Error: {e}");
        return e;

    return "null"


def makeImg(imgPath: str, savePath: str, text: str, La: int, Lo: int, fontSize: int = 50) -> None:
    img = Image.open(imgPath);
    draw = ImageDraw.Draw(img);

    font = ImageFont.truetype(r"C:\Windows\Fonts\malgunbd.ttf", fontSize);
    textLaLo = (La, Lo);
    draw.text(textLaLo, text, font=font, fill="black");
    img.save(savePath);


def setWallpaper(imgPath: str) -> None:
    imgXPath = os.path.abspath(imgPath);
    ctypes.windll.user32.SystemParametersInfoW(20, 0, imgXPath, 0);


def main() -> None:
    CDW1 = getMeal(getCurrentDate(), "1", "메뉴"); # 조식
    CDW2 = getMeal(getCurrentDate(), "2", "메뉴"); # 중식
    CDW3 = getMeal(getCurrentDate(), "3", "메뉴"); # 석식

    makeImg("base.png", f"{getCurrentDate()} 급식.png", f"조식\n\n{CDW1}\n\n\n중식\n\n{CDW2} \n\n\n석식\n\n{CDW3}", 200, 120, 60); # 급식
    makeImg(f"{getCurrentDate()} 급식.png", f"{getCurrentDate()}.png", f"{getCurrentDate()}", 1600, 100, 130); # 날짜

    os.remove(f"{getCurrentDate()} 급식.png");

    setWallpaper(f"{getCurrentDate()}.png");

if __name__ == "__main__":
    main();