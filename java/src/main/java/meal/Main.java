package meal;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * NEIS 오픈 API에서 오늘 급식을 받아 배경 이미지에 그린 뒤 Windows 바탕화면으로 설정한다.
 * python/main.pyw 와 같은 동작을 표준 JDK 만으로 구현한 것.
 */
public final class Main {

    /** 시도교육청 코드 */
    private static final String ATPT_OFCDC_SC_CODE = "I10";
    /** 표준학교 코드 */
    private static final String SD_SCHUL_CODE = "9300058";

    private static final String FONT_PATH = "C:\\Windows\\Fonts\\malgunbd.ttf";

    /** 배치는 python/main.pyw 의 makeImg 호출 좌표와 같다. */
    private static final int MEAL_X = 200, MEAL_Y = 120, MEAL_SIZE = 60;
    private static final int DATE_X = 1600, DATE_Y = 100, DATE_SIZE = 130;

    private static final int SPI_SETDESKWALLPAPER = 0x0014;
    private static final int SPIF_UPDATEINIFILE = 0x01;
    private static final int SPIF_SENDWININICHANGE = 0x02;

    private static final Pattern DDISH_NM =
            Pattern.compile("\"DDISH_NM\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        Path outDir = defaultOutDir();
        boolean setWallpaper = true;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--date" -> date = args[++i];
                case "--out" -> outDir = Path.of(args[++i]);
                case "--no-wallpaper" -> setWallpaper = false;
                case "--help", "-h" -> {
                    System.out.println("사용법: MealWallpaper [--date YYYYMMDD] [--out <폴더>] [--no-wallpaper]");
                    return;
                }
                default -> throw new IllegalArgumentException("알 수 없는 인자: " + args[i]);
            }
        }

        String breakfast, lunch, dinner;
        HttpClient http = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            String d = date;
            Future<String> b = pool.submit(() -> fetchMenu(http, d, "1"));
            Future<String> l = pool.submit(() -> fetchMenu(http, d, "2"));
            Future<String> s = pool.submit(() -> fetchMenu(http, d, "3"));
            breakfast = b.get();
            lunch = l.get();
            dinner = s.get();
        }

        String menu = "조식\n\n" + breakfast + "\n\n\n중식\n\n" + lunch + " \n\n\n석식\n\n" + dinner;

        BufferedImage img = readBaseImage();
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(java.awt.Color.BLACK);
        drawText(g, menu, MEAL_X, MEAL_Y, loadFont(MEAL_SIZE));
        drawText(g, date, DATE_X, DATE_Y, loadFont(DATE_SIZE));
        g.dispose();

        Files.createDirectories(outDir);
        Path png = outDir.resolve(date + ".png");
        writePng(img, png);
        System.out.println("생성: " + png.toAbsolutePath());

        if (setWallpaper) {
            setWallpaper(png);
            System.out.println("바탕화면 적용 완료");
        }
    }

    /** 실행 환경과 무관하게 쓰기 가능한 기본 출력 폴더. */
    private static Path defaultOutDir() {
        String localAppData = System.getenv("LOCALAPPDATA");
        Path base = (localAppData == null || localAppData.isBlank())
                ? Path.of(System.getProperty("user.home"))
                : Path.of(localAppData);
        return base.resolve("MealWallpaper");
    }

    private static BufferedImage readBaseImage() throws Exception {
        try (InputStream in = Main.class.getResourceAsStream("/base.png")) {
            if (in == null) {
                throw new IllegalStateException("base.png 리소스를 찾을 수 없습니다.");
            }
            return ImageIO.read(in);
        }
    }

    /** ImageIO.write 기본값은 압축이 약해 파일이 커진다. PNG 는 무손실이라 최대 압축으로 쓴다. */
    private static void writePng(BufferedImage image, Path out) throws Exception {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionType(param.getCompressionTypes()[0]);
            param.setCompressionQuality(0.0f);
        }
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out.toFile())) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    private static Font loadFont(int size) throws Exception {
        return Font.createFont(Font.TRUETYPE_FONT, new File(FONT_PATH)).deriveFont((float) size);
    }

    /**
     * Pillow 의 draw.text 와 맞추기 위해 좌표를 텍스트 상단으로 잡고 줄바꿈을 직접 처리한다.
     * Graphics2D.drawString 은 기준선(baseline) 기준이라 ascent 만큼 내려서 그린다.
     */
    private static void drawText(Graphics2D g, String text, int x, int y, Font font) {
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        int baseline = y + fm.getAscent();
        for (String line : text.split("\n", -1)) {
            g.drawString(line, x, baseline);
            baseline += fm.getHeight();
        }
    }

    private static String fetchMenu(HttpClient http, String date, String mealScCode) {
        String url = "https://open.neis.go.kr/hub/mealServiceDietInfo?"
                + "&Type=json"
                + "&MMEAL_SC_CODE=" + mealScCode
                + "&ATPT_OFCDC_SC_CODE=" + ATPT_OFCDC_SC_CODE
                + "&SD_SCHUL_CODE=" + SD_SCHUL_CODE
                + "&MLSV_YMD=" + date;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();
            String body = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
            Matcher m = DDISH_NM.matcher(body);
            if (!m.find()) {
                return "";  // 급식이 없는 날은 NEIS 가 INFO-200 을 준다.
            }
            return unescapeJson(m.group(1)).replace("<br/>", "\n");
        } catch (Exception e) {
            System.err.println("급식 조회 실패 (MMEAL_SC_CODE=" + mealScCode + "): " + e);
            return "";
        }
    }

    /** 의존성 없이 쓰기 위한 최소 구현. DDISH_NM 값 하나를 푸는 용도로만 쓴다. */
    private static String unescapeJson(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            char next = raw.charAt(++i);
            switch (next) {
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'u' -> {
                    sb.append((char) Integer.parseInt(raw.substring(i + 1, i + 5), 16));
                    i += 4;
                }
                default -> sb.append(next);  // \" \\ \/
            }
        }
        return sb.toString();
    }

    /** user32.dll 의 SystemParametersInfoW 를 FFM(java.lang.foreign)으로 직접 호출한다. */
    private static void setWallpaper(Path image) throws Exception {
        try (Arena arena = Arena.ofConfined()) {
            SymbolLookup user32 = SymbolLookup.libraryLookup("user32.dll", arena);
            MethodHandle systemParametersInfoW = Linker.nativeLinker().downcallHandle(
                    user32.find("SystemParametersInfoW").orElseThrow(
                            () -> new IllegalStateException("SystemParametersInfoW 심볼을 찾을 수 없습니다.")),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT,
                            ValueLayout.JAVA_INT,    // uiAction
                            ValueLayout.JAVA_INT,    // uiParam
                            ValueLayout.ADDRESS,     // pvParam
                            ValueLayout.JAVA_INT));  // fWinIni

            MemorySegment path = wideString(arena, image.toAbsolutePath().toString());
            int ok;
            try {
                ok = (int) systemParametersInfoW.invokeExact(
                        SPI_SETDESKWALLPAPER, 0, path, SPIF_UPDATEINIFILE | SPIF_SENDWININICHANGE);
            } catch (Throwable t) {
                throw new IllegalStateException("SystemParametersInfoW 호출 중 오류가 발생했습니다.", t);
            }
            if (ok == 0) {
                throw new IllegalStateException("SystemParametersInfoW 호출이 실패했습니다.");
            }
        }
    }

    /** Win32 W 계열 API 가 요구하는 널 종료 UTF-16 문자열. */
    private static MemorySegment wideString(Arena arena, String s) {
        MemorySegment seg = arena.allocateArray(ValueLayout.JAVA_CHAR, s.length() + 1L);
        for (int i = 0; i < s.length(); i++) {
            seg.setAtIndex(ValueLayout.JAVA_CHAR, i, s.charAt(i));
        }
        seg.setAtIndex(ValueLayout.JAVA_CHAR, s.length(), '\0');
        return seg;
    }
}
