package com.licham.widget

import android.content.Context
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

data class LunarPoint(
    val day: Int,
    val month: Int,
    val year: Int,
    val isLeap: Boolean,
    val jd: Int
)

data class UpcomingHoliday(
    val name: String,
    val lunarLabel: String,
    val solarLabel: String,
    val daysLeft: Int,
    val isMajor: Boolean
)

data class DayAlmanac(
    val solarDay: Int,
    val solarMonth: Int,
    val solarYear: Int,
    val lunarDay: Int,
    val lunarMonth: Int,
    val lunarYear: Int,
    val isLeapMonth: Boolean,
    val dayCanChi: String,
    val monthCanChi: String,
    val yearCanChi: String,
    val napAm: String,
    val solarTerm: String,
    val isHoangDao: Boolean,
    val dayStarName: String,
    val trucName: String,
    val trucQuality: String,
    val trucDesc: String,
    val sao28Name: String,
    val sao28Good: Boolean,
    val sao28Desc: String,
    val hoangDaoHours: String,
    val hyThanDir: String,
    val taiThanDir: String,
    val conflictingAges: String,
    val badDayWarnings: List<String>,
    val overallRating: String,
    val shouldDo: String,
    val shouldAvoid: String,
    val lunarHoliday: String?,
    val solarHoliday: String?,
    val proverb: String
) {
    fun primaryHolidayLabel(): String? {
        return lunarHoliday ?: solarHoliday
    }

    fun isNotableLunarDay(): Boolean {
        return lunarHoliday != null || lunarDay == 1 || lunarDay == 15
    }
}

object LunarAlmanac {

    private const val PREF_NAME = "lich_am_almanac_prefs"
    private const val KEY_SHOW_EXTRA_ON_WIDGET = "show_extra_on_widget"

    fun isShowExtraOnWidget(context: Context): Boolean {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOW_EXTRA_ON_WIDGET, true)
    }

    fun setShowExtraOnWidget(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SHOW_EXTRA_ON_WIDGET, enabled)
            .apply()
    }

    private val CAN = arrayOf("Giáp", "Ất", "Bính", "Đinh", "Mậu", "Kỷ", "Canh", "Tân", "Nhâm", "Quý")
    private val CHI = arrayOf("Tý", "Sửu", "Dần", "Mão", "Thìn", "Tỵ", "Ngọ", "Mùi", "Thân", "Dậu", "Tuất", "Hợi")
    private val HOUR_RANGES = arrayOf(
        "Tý (23-1h)", "Sửu (1-3h)", "Dần (3-5h)", "Mão (5-7h)",
        "Thìn (7-9h)", "Tỵ (9-11h)", "Ngọ (11-13h)", "Mùi (13-15h)",
        "Thân (15-17h)", "Dậu (17-19h)", "Tuất (19-21h)", "Hợi (21-23h)"
    )

    // 12 Thần Sát Hoàng Đạo / Hắc Đạo theo Chi ngày & tháng Âm lịch
    private val STAR12_NAMES = arrayOf(
        "Thanh Long", "Minh Đường", "Thiên Hình", "Chu Tước",
        "Kim Quỹ", "Kim Đường", "Bạch Hổ", "Ngọc Đường",
        "Thiên Lao", "Nguyên Vũ", "Tư Mệnh", "Câu Trận"
    )
    private val HOANG_DAO_SET = setOf(0, 1, 4, 5, 7, 10)

    // 6 Giờ Hoàng Đạo theo Chi ngày (dayChi % 6)
    private val HOANG_DAO_HOURS_BY_CHI6 = arrayOf(
        intArrayOf(0, 1, 3, 6, 8, 9),   // Tý, Ngọ
        intArrayOf(2, 3, 5, 8, 10, 11), // Sửu, Mùi
        intArrayOf(0, 1, 4, 5, 7, 10),  // Dần, Thân
        intArrayOf(0, 2, 3, 6, 7, 9),   // Mão, Dậu
        intArrayOf(2, 4, 5, 8, 9, 11),  // Thìn, Tuất
        intArrayOf(1, 4, 6, 7, 10, 11)  // Tỵ, Hợi
    )

    // 12 Trực Kiến Trừ (name, quality, short desc, shouldDo, shouldAvoid)
    private data class TrucInfo(
        val name: String,
        val quality: String,
        val desc: String,
        val goodFor: String,
        val badFor: String
    )

    private val TRUC_LIST = arrayOf(
        TrucInfo("Kiến", "Trung bình", "Khởi đầu thuận lợi cho xuất hành, trồng cây", "Xuất hành, nhậm chức, ký kết nhỏ, gieo trồng", "Động thổ, đào giếng, chôn cất"),
        TrucInfo("Trừ", "Tốt", "Tẩy trừ điều xấu, tốt cho chữa bệnh, dọn dẹp", "Chữa bệnh, dọn dẹp nhà cửa, giải hạn, cắt may", "Khai trương lớn, cưới hỏi, xuất tiền lớn"),
        TrucInfo("Mãn", "Tốt", "Đầy đủ viên mãn, tốt cho cầu tài và lễ nghi", "Cúng tế, cầu phúc, mở kho, giao dịch, cưới hỏi", "Động thổ, kiện tụng, nhậm chức"),
        TrucInfo("Bình", "Trung bình", "Bình hòa ổn định, hợp việc sửa sang đường sá", "Tu sửa nhà cửa, làm đường, giao tế nhẹ nhàng", "Khai trương lớn, khởi công đại sự"),
        TrucInfo("Định", "Tốt", "Vững bền định ổn, rất tốt cho cưới hỏi, ký kết", "Cưới hỏi, ký hợp đồng, nhập học, giao dịch", "Kiện tụng, đi xa dài ngày, chữa bệnh nặng"),
        TrucInfo("Chấp", "Tốt", "Nắm giữ bền chặt, hợp khởi công và xây dựng", "Khởi công, xây dựng, trồng trọt, tuyển dụng", "Xuất hành đi xa, chuyển nhà, mở kho chi lớn"),
        TrucInfo("Phá", "Xấu", "Dễ đổ vỡ, chỉ hợp phá dỡ công trình cũ", "Phá dỡ nhà cũ, chữa bệnh, dọn bỏ đồ hư hỏng", "Cưới hỏi, khai trương, ký kết, xuất hành"),
        TrucInfo("Nguy", "Xấu", "Tiềm ẩn trắc trở, nên thận trọng trong mọi việc", "Cúng tế cầu an, làm việc nội bộ ổn định", "Leo cao, đi sông biển, khởi công, cưới hỏi"),
        TrucInfo("Thành", "Rất tốt", "Thành tựu vẹn toàn, đại cát cho mọi việc lớn", "Khai trương, cưới hỏi, nhập trạch, ký kết, xuất hành", "Kiện tụng, tranh chấp"),
        TrucInfo("Thu", "Trung bình", "Thu hoạch gom góp, hợp thu nợ và nhập kho", "Thu hoạch, nhập kho, đòi nợ, ký nhận hàng", "Chôn cất, khởi công mới, xuất hành xa"),
        TrucInfo("Khai", "Rất tốt", "Mở mang hanh thông, rất tốt cho khởi sự", "Khai trương, xuất hành, động thổ, cưới hỏi, nhậm chức", "An táng, phá dỡ"),
        TrucInfo("Bế", "Xấu", "Khép kín bế tắc, hợp đắp đê vá tường, nghỉ ngơi", "Đắp tường, tu sửa nhỏ, thu dọn, nghỉ ngơi", "Khai trương, cưới hỏi, xuất hành, động thổ")
    )

    // 28 Sao Nhị Thập Bát Tú (name, isGood, short description)
    private data class Sao28Info(val name: String, val isGood: Boolean, val desc: String)

    private val SAO28_LIST = arrayOf(
        Sao28Info("Giác (Mộc)", true, "Cát tinh — tốt cho cưới hỏi, xuất hành, thi cử"),
        Sao28Info("Cang (Kim)", false, "Hung tinh — kỵ cưới hỏi, động thổ, đi thuyền"),
        Sao28Info("Đê (Thổ)", false, "Hung tinh — tránh khởi công, cưới gả, xuất hành xa"),
        Sao28Info("Phòng (Nhật)", true, "Đại cát — rất tốt cho xây dựng, cưới hỏi, chuyển nhà"),
        Sao28Info("Tâm (Nguyệt)", false, "Hung tinh — tránh khởi sự lớn, dễ sinh tranh cãi"),
        Sao28Info("Vĩ (Hỏa)", true, "Cát tinh — vượng tài lộc, hợp cưới hỏi, xây dựng"),
        Sao28Info("Cơ (Thủy)", true, "Cát tinh — tốt cho tu sửa, khai trương, giao dịch"),
        Sao28Info("Đẩu (Mộc)", true, "Cát tinh — hợp xây dựng, cắt may, giao dịch"),
        Sao28Info("Ngưu (Kim)", false, "Hung tinh — kỵ cưới hỏi, xây nhà, chuyển chỗ ở"),
        Sao28Info("Nữ (Thổ)", false, "Hung tinh — tránh cưới hỏi, tranh chấp, khởi công"),
        Sao28Info("Hư (Nhật)", false, "Hung tinh — không thuận cho xây dựng, khai trương"),
        Sao28Info("Nguy (Nguyệt)", false, "Hung tinh — thận trọng khi đi xa, làm việc trên cao"),
        Sao28Info("Thất (Hỏa)", true, "Đại cát — rất tốt cho khởi công, cưới hỏi, nhập trạch"),
        Sao28Info("Bích (Thủy)", true, "Đại cát — vượng kinh doanh, cưới hỏi, xây dựng"),
        Sao28Info("Khuê (Mộc)", false, "Bình hung — tránh khởi công, đào đất, kiện tụng"),
        Sao28Info("Lâu (Kim)", true, "Cát tinh — hợp cưới hỏi, tu sửa, xây cất"),
        Sao28Info("Vị (Thổ)", true, "Cát tinh — thuận lợi cho khởi công, cưới hỏi, xuất hành"),
        Sao28Info("Mão (Nhật)", false, "Hung tinh — tránh mở cửa hàng, cưới hỏi, xây cất"),
        Sao28Info("Tất (Nguyệt)", true, "Đại cát — tốt cho khai trương, cưới hỏi, xây dựng"),
        Sao28Info("Chủy (Hỏa)", false, "Hung tinh — kỵ khởi công, sửa nhà, kiện tụng"),
        Sao28Info("Sâm (Thủy)", true, "Cát tinh — hợp thi cử, xây dựng, nhậm chức"),
        Sao28Info("Tỉnh (Mộc)", true, "Cát tinh — tốt cho xây dựng, cầu tài, gieo trồng"),
        Sao28Info("Quỷ (Kim)", false, "Hung tinh — kỵ cưới hỏi, khai trương, xuất hành"),
        Sao28Info("Liễu (Thổ)", false, "Hung tinh — tránh xây dựng, cưới hỏi, mở hàng"),
        Sao28Info("Tinh (Nhật)", false, "Bình hung — chỉ hợp làm việc nhỏ, tránh cưới hỏi"),
        Sao28Info("Trương (Nguyệt)", true, "Đại cát — rất tốt cho cưới hỏi, khai trương, ký kết"),
        Sao28Info("Dực (Hỏa)", false, "Hung tinh — tránh cưới gả, làm nhà, xuất hành xa"),
        Sao28Info("Chẩn (Thủy)", true, "Cát tinh — tốt cho xuất hành, cưới hỏi, nhập trạch")
    )

    // 30 Ngũ Hành Nạp Âm (60 Hoa Giáp chia 2)
    private val NAP_AM_30 = arrayOf(
        "Hải Trung Kim", "Lư Trung Hỏa", "Đại Lâm Mộc", "Lộ Bàng Thổ", "Kiếm Phong Kim",
        "Sơn Đầu Hỏa", "Giản Hạ Thủy", "Thành Đầu Thổ", "Bạch Lạp Kim", "Dương Liễu Mộc",
        "Tuyền Trung Thủy", "Ốc Thượng Thổ", "Tích Lịch Hỏa", "Tùng Bách Mộc", "Trường Lưu Thủy",
        "Sa Trung Kim", "Sơn Hạ Hỏa", "Bình Địa Mộc", "Bích Thượng Thổ", "Kim Bạch Kim",
        "Phú Đăng Hỏa", "Thiên Hà Thủy", "Đại Trạch Thổ", "Thoa Xuyến Kim", "Tang Đố Mộc",
        "Đại Khê Thủy", "Sa Trung Thổ", "Thiên Thượng Hỏa", "Thạch Lựu Mộc", "Đại Hải Thủy"
    )

    // 24 Tiết Khí (tính theo kinh độ Mặt Trời mỗi 15° kể từ 0° Xuân Phân)
    private val SOLAR_TERMS = arrayOf(
        "Xuân Phân", "Thanh Minh", "Cốc Vũ", "Lập Hạ", "Tiểu Mãn", "Mang Chủng",
        "Hạ Chí", "Tiểu Thử", "Đại Thử", "Lập Thu", "Xử Thử", "Bạch Lộ",
        "Thu Phân", "Hàn Lộ", "Sương Giáng", "Lập Đông", "Tiểu Tuyết", "Đại Tuyết",
        "Đông Chí", "Tiểu Hàn", "Đại Hàn", "Lập Xuân", "Vũ Thủy", "Kinh Trập"
    )

    // Hướng xuất hành theo Thiên Can ngày (0..9)
    private val HY_THAN = arrayOf(
        "Đông Bắc", "Tây Bắc", "Tây Nam", "Chính Nam", "Đông Nam",
        "Đông Bắc", "Tây Bắc", "Tây Nam", "Chính Nam", "Đông Nam"
    )
    private val TAI_THAN = arrayOf(
        "Đông Nam", "Đông Nam", "Chính Đông", "Chính Đông", "Chính Bắc",
        "Chính Nam", "Tây Nam", "Tây Nam", "Chính Tây", "Tây Bắc"
    )

    // Ngày Sát Chủ (Chi ngày theo tháng Âm lịch 1..12)
    private val SAT_CHU_CHI = intArrayOf(5, 0, 7, 3, 8, 10, 1, 11, 6, 9, 2, 4)
    // Ngày Thọ Tử (Chi ngày theo tháng Âm lịch 1..12)
    private val THO_TU_CHI = intArrayOf(10, 4, 11, 5, 0, 6, 1, 7, 2, 8, 3, 9)

    private val TAM_NUONG_DAYS = setOf(3, 7, 13, 18, 22, 27)
    private val NGUYET_KY_DAYS = setOf(5, 14, 23)

    // 72 câu ngạn ngữ, tục ngữ, danh ngôn ý nghĩa đổi mỗi ngày
    private val PROVERBS = arrayOf(
        "Có công mài sắt, có ngày nên kim.",
        "Đi một ngày đàng, học một sàng khôn.",
        "Lửa thử vàng, gian nan thử sức.",
        "Tâm an thì vạn sự an, lòng tĩnh thì trăm việc thuận.",
        "Thất bại là mẹ thành công, kiên trì là chìa khóa mở lối.",
        "Một cây làm chẳng nên non, ba cây chụm lại nên hòn núi cao.",
        "Uống nước nhớ nguồn, ăn quả nhớ kẻ trồng cây.",
        "Chậm mà chắc còn hơn nhanh mà ẩu.",
        "Học ăn, học nói, học gói, học mở.",
        "Gieo nhân nào gặt quả nấy, làm việc thiện ắt gặp phúc lành.",
        "Đường đi khó không khó vì ngăn sông cách núi, mà khó vì lòng người ngại núi e sông.",
        "Biết người biết ta, trăm trận trăm thắng.",
        "Thuận vợ thuận chồng, tát biển Đông cũng cạn.",
        "Ở hiền gặp lành, tích đức cho đời sau thêm bền vững.",
        "Lời nói chẳng mất tiền mua, lựa lời mà nói cho vừa lòng nhau.",
        "Một điều nhịn là chín điều lành.",
        "Nước chảy đá mòn, việc lớn bắt đầu từ bước nhỏ mỗi ngày.",
        "Muốn đi nhanh hãy đi một mình, muốn đi xa hãy đi cùng nhau.",
        "Đói cho sạch, rách cho thơm.",
        "Thương người như thể thương thân.",
        "Tốt gỗ hơn tốt nước sơn.",
        "Kiến tha lâu cũng đầy tổ.",
        "Không thầy đố mày làm nên, học thầy không tày học bạn.",
        "Gần mực thì đen, gần đèn thì rạng.",
        "Cái khó ló cái khôn, càng thử thách càng trưởng thành.",
        "Sức khỏe là vốn quý nhất, giữ thân khỏe để tâm luôn sáng.",
        "Giữ chữ tín hơn giữ vàng, mất chữ tín là mất tất cả.",
        "Người biết đủ là người giàu có nhất.",
        "Hôm nay cố gắng thêm một chút, ngày mai bớt đi một phần âu lo.",
        "Mỗi ngày mới là một trang giấy trắng để viết tiếp điều tốt đẹp.",
        "Nói ít làm nhiều, lấy kết quả thay cho lời hứa.",
        "Sông sâu tĩnh lặng, lúa chín cúi đầu.",
        "Khiêm tốn là thước đo của người hiểu biết.",
        "Cây ngay không sợ chết đứng.",
        "Bình tĩnh trước sóng gió mới nhìn rõ bến bờ.",
        "Hạnh phúc không ở đích đến, mà ở từng bước đi bình an.",
        "Chọn bạn mà chơi, chọn nơi mà ở.",
        "Thời gian là tài sản công bằng nhất, hãy dùng cho điều xứng đáng.",
        "Việc hôm nay chớ để ngày mai.",
        "Đức năng thắng số, lòng ngay thẳng tự khắc hóa giải gian nan.",
        "Càng đi nhiều càng thấy mình nhỏ bé, càng học nhiều càng thấy mình cần khiêm nhường.",
        "Một nụ cười bằng mười thang thuốc bổ.",
        "Tích tiểu thành đại, góp gió thành bão.",
        "Trăm nghe không bằng một thấy, trăm thấy không bằng một lần tự làm.",
        "Dục tốc bất đạt, việc lớn cần bền gan vững chí.",
        "Khéo ăn thì no, khéo co thì ấm.",
        "An cư rồi mới lạc nghiệp.",
        "Gia hòa vạn sự hưng.",
        "Mưu sự tại nhân, thành sự tại thiên — cứ làm hết sức mình trước đã.",
        "Đừng đợi hoàn hảo mới bắt đầu, hãy bắt đầu để trở nên tốt hơn.",
        "Vàng thật không sợ lửa, người ngay không ngại thử thách.",
        "Biết lắng nghe là khởi đầu của sự khôn ngoan.",
        "Thắng không kiêu, bại không nản.",
        "Của bền tại người, phúc bền tại tâm.",
        "Làm việc bằng cả tấm lòng, thành quả sẽ tự tìm đến.",
        "Sáng gieo hạt chăm chỉ, tối gặt mùa bình yên.",
        "Nhẫn một chút sóng yên gió lặng, lùi một bước biển rộng trời cao.",
        "Giàu vì bạn, sang vì vợ, vững vì chính đôi tay mình.",
        "Sống chân thành chẳng lo đường hẹp.",
        "Ánh nắng luôn trở lại sau những ngày mưa giông.",
        "Đặt tâm vào việc nhỏ, việc lớn tự khắc thành.",
        "Người kiên nhẫn luôn đến đích sớm hơn kẻ nóng vội.",
        "Cảm ơn quá khứ đã rèn giũa, trân trọng hiện tại để vững bước tương lai.",
        "Đôi tay khéo léo kết hợp cái đầu tỉnh táo tạo nên giá trị bền lâu.",
        "Mỗi sáng thức dậy bình an đã là một món quà quý.",
        "Chữ tâm kia mới bằng ba chữ tài.",
        "Trời không phụ lòng người có chí.",
        "Đừng so sánh mình với người khác, hãy tốt hơn chính mình của hôm qua.",
        "Làm chủ cảm xúc là làm chủ vận mệnh.",
        "Cho đi sự tử tế sẽ nhận lại bình an.",
        "Bão lớn đến đâu rồi cũng tan, chỉ người vững tâm là còn đứng vững.",
        "Kỷ luật tạo nên tự do, đều đặn tạo nên kỳ tích."
    )

    // ============================================================
    // HỒ NGỌC ĐỨC ASTRONOMICAL SOLAR -> LUNAR (UTC+7)
    // ============================================================
    fun jdFromDate(dd: Int, mm: Int, yy: Int): Int {
        val a = (14 - mm) / 12
        val y = yy + 4800 - a
        val m = mm + 12 * a - 3
        var jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
        if (jd < 2299161) {
            jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083
        }
        return jd
    }

    private fun newMoon(k: Int): Double {
        val t = k / 1236.85
        val t2 = t * t
        val t3 = t2 * t
        val dr = PI / 180.0
        var jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * t2 - 0.000000155 * t3
        jd1 += 0.00033 * sin((166.56 + 132.87 * t - 0.009173 * t2) * dr)
        val m = 359.2242 + 29.10535608 * k - 0.0000333 * t2 - 0.00000347 * t3
        val mpr = 306.0253 + 385.81691806 * k + 0.0107306 * t2 + 0.00001236 * t3
        val f = 21.2964 + 390.67050646 * k - 0.0016528 * t2 - 0.00000239 * t3
        var c1 = (0.1734 - 0.000393 * t) * sin(m * dr) + 0.0021 * sin(2 * dr * m)
        c1 = c1 - 0.4068 * sin(mpr * dr) + 0.0161 * sin(dr * 2 * mpr)
        c1 -= 0.0004 * sin(dr * 3 * mpr)
        c1 = c1 + 0.0104 * sin(dr * 2 * f) - 0.0051 * sin(dr * (m + mpr))
        c1 = c1 - 0.0074 * sin(dr * (m - mpr)) + 0.0004 * sin(dr * (2 * f + m))
        c1 = c1 - 0.0004 * sin(dr * (2 * f - m)) - 0.0006 * sin(dr * (2 * f + mpr))
        c1 = c1 + 0.0010 * sin(dr * (2 * f - mpr)) + 0.0005 * sin(dr * (2 * mpr + m))
        val deltaT = if (t < -11) {
            0.001 + 0.000839 * t + 0.0002261 * t2 - 0.00000845 * t3 - 0.000000081 * t * t3
        } else {
            -0.000278 + 0.000265 * t + 0.000262 * t2
        }
        return jd1 + c1 - deltaT
    }

    private fun sunLongitudeRad(jdn: Double): Double {
        val t = (jdn - 2451545.0) / 36525.0
        val t2 = t * t
        val dr = PI / 180.0
        val m = 357.52910 + 35999.05030 * t - 0.0001559 * t2 - 0.00000048 * t * t2
        val l0 = 280.46645 + 36000.76983 * t + 0.0003032 * t2
        var dl = (1.914600 - 0.004817 * t - 0.000014 * t2) * sin(dr * m)
        dl += (0.019993 - 0.000101 * t) * sin(dr * 2 * m) + 0.000290 * sin(dr * 3 * m)
        var l = (l0 + dl) * dr
        l -= PI * 2.0 * floor(l / (PI * 2.0))
        return l
    }

    private fun getSunLongitudeSector(dayNumber: Int, tz: Double = 7.0): Int {
        return floor(sunLongitudeRad(dayNumber - 0.5 - tz / 24.0) / PI * 6.0).toInt()
    }

    private fun getSolarTermIndex(dayNumber: Int, tz: Double = 7.0): Int {
        val deg = sunLongitudeRad(dayNumber - 0.5 - tz / 24.0) * 180.0 / PI
        return (floor(deg / 15.0).toInt() % 24 + 24) % 24
    }

    private fun getNewMoonDay(k: Int, tz: Double = 7.0): Int {
        return floor(newMoon(k) + 0.5 + tz / 24.0).toInt()
    }

    private fun getLunarMonth11(yy: Int, tz: Double = 7.0): Int {
        val off = jdFromDate(31, 12, yy) - 2415021
        val k = floor(off / 29.530588853).toInt()
        var nm = getNewMoonDay(k, tz)
        if (getSunLongitudeSector(nm, tz) >= 9) {
            nm = getNewMoonDay(k - 1, tz)
        }
        return nm
    }

    private fun getLeapMonthOffset(a11: Int, tz: Double = 7.0): Int {
        val k = floor((a11 - 2415021.076998695) / 29.530588853 + 0.5).toInt()
        var last: Int
        var i = 1
        var arc = getSunLongitudeSector(getNewMoonDay(k + i, tz), tz)
        do {
            last = arc
            i++
            arc = getSunLongitudeSector(getNewMoonDay(k + i, tz), tz)
        } while (arc != last && i < 14)
        return i - 1
    }

    fun getLunarPoint(dd: Int, mm: Int, yy: Int, tz: Double = 7.0): LunarPoint {
        val dayNum = jdFromDate(dd, mm, yy)
        val k = floor((dayNum - 2415021.076998695) / 29.530588853).toInt()
        var monthStart = getNewMoonDay(k + 1, tz)
        if (monthStart > dayNum) {
            monthStart = getNewMoonDay(k, tz)
        }
        var a11 = getLunarMonth11(yy, tz)
        var b11 = a11
        var lunarYear: Int
        if (a11 >= monthStart) {
            lunarYear = yy
            a11 = getLunarMonth11(yy - 1, tz)
        } else {
            lunarYear = yy + 1
            b11 = getLunarMonth11(yy + 1, tz)
        }
        val lunarDay = dayNum - monthStart + 1
        val diff = floor((monthStart - a11) / 29.0).toInt()
        var lunarLeap = false
        var lunarMonth = diff + 11
        if (b11 - a11 > 365) {
            val leapOff = getLeapMonthOffset(a11, tz)
            if (diff >= leapOff) {
                lunarMonth = diff + 10
                if (diff == leapOff) {
                    lunarLeap = true
                }
            }
        }
        if (lunarMonth > 12) lunarMonth -= 12
        if (lunarMonth >= 11 && diff < 4) lunarYear -= 1
        return LunarPoint(lunarDay, lunarMonth, lunarYear, lunarLeap, dayNum)
    }

    private fun lunarPointFromJd(jd: Int): LunarPoint {
        val cal = calendarFromJd(jd)
        return getLunarPoint(
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.YEAR)
        )
    }

    private fun calendarFromJd(jd: Int): Calendar {
        val b: Int
        val c: Int
        if (jd > 2299160) {
            val a = jd + 32044
            b = (4 * a + 3) / 146097
            c = a - (b * 146097) / 4
        } else {
            b = 0
            c = jd + 32082
        }
        val d = (4 * c + 3) / 1461
        val e = c - (1461 * d) / 4
        val m = (5 * e + 2) / 153
        val day = e - (153 * m + 2) / 5 + 1
        val month = m + 3 - 12 * (m / 10)
        val year = b * 100 + d - 4800 + m / 10
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
        }
    }

    // ============================================================
    // NGÀY LỄ ÂM LỊCH & DƯƠNG LỊCH VIỆT NAM
    // ============================================================
    fun getLunarHoliday(day: Int, month: Int, isLeap: Boolean, jd: Int): String? {
        if (isLeap) return null
        // Kiểm tra Giao Thừa (ngày cuối tháng Chạp: 29 hoặc 30 Tết)
        if (month == 12 && day >= 29) {
            val tomorrow = lunarPointFromJd(jd + 1)
            if (tomorrow.day == 1 && tomorrow.month == 1) {
                return "Đêm Giao Thừa (Tất Niên)"
            }
        }
        return when (month * 100 + day) {
            101 -> "Tết Nguyên Đán (Mùng 1 Tết)"
            102 -> "Mùng 2 Tết Nguyên Đán"
            103 -> "Mùng 3 Tết Nguyên Đán"
            110 -> "Ngày vía Thần Tài"
            115 -> "Tết Nguyên Tiêu (Rằm tháng Giêng)"
            303 -> "Tết Hàn Thực"
            310 -> "Giỗ Tổ Hùng Vương"
            415 -> "Đại lễ Phật Đản"
            505 -> "Tết Đoan Ngọ"
            707 -> "Lễ Thất Tịch"
            715 -> "Lễ Vu Lan (Rằm tháng Bảy)"
            815 -> "Tết Trung Thu"
            909 -> "Tết Trùng Cửu"
            1010 -> "Tết Trùng Thập (Cơm mới)"
            1015 -> "Tết Hạ Nguyên (Rằm tháng Mười)"
            1223 -> "Tiễn Ông Công Ông Táo"
            else -> null
        }
    }

    fun getMajorSolarHoliday(day: Int, month: Int): String? {
        return when (month * 100 + day) {
            101 -> "Tết Dương Lịch"
            214 -> "Lễ Tình Nhân (Valentine)"
            308 -> "Quốc tế Phụ nữ 8/3"
            430 -> "Giải phóng miền Nam 30/4"
            501 -> "Quốc tế Lao động 1/5"
            601 -> "Quốc tế Thiếu nhi 1/6"
            902 -> "Quốc khánh 2/9"
            1020 -> "Ngày Phụ nữ Việt Nam 20/10"
            1120 -> "Ngày Nhà giáo Việt Nam 20/11"
            1225 -> "Lễ Giáng Sinh"
            else -> null
        }
    }

    fun isLunarSpecialDay(dd: Int, mm: Int, yy: Int): Boolean {
        val lp = getLunarPoint(dd, mm, yy)
        if (lp.day == 1 || lp.day == 15) return true
        return getLunarHoliday(lp.day, lp.month, lp.isLeap, lp.jd) != null
    }

    fun getUpcomingLunarHolidays(fromCal: Calendar, limit: Int = 5): List<UpcomingHoliday> {
        val result = ArrayList<UpcomingHoliday>(limit)
        val startJd = jdFromDate(
            fromCal.get(Calendar.DAY_OF_MONTH),
            fromCal.get(Calendar.MONTH) + 1,
            fromCal.get(Calendar.YEAR)
        )
        // Quét 365 ngày tới tìm lễ Âm lịch (ưu tiên lễ lớn; vẫn thêm Mùng 1 / Rằm gần nhất)
        var addedSocOrVong = 0
        for (offset in 1..365) {
            val jd = startJd + offset
            val c = calendarFromJd(jd)
            val sd = c.get(Calendar.DAY_OF_MONTH)
            val sm = c.get(Calendar.MONTH) + 1
            val sy = c.get(Calendar.YEAR)
            val lp = getLunarPoint(sd, sm, sy)
            val majorName = getLunarHoliday(lp.day, lp.month, lp.isLeap, jd)
            if (majorName != null) {
                val monthSuffix = if (lp.isLeap) "N" else ""
                result.add(
                    UpcomingHoliday(
                        name = majorName,
                        lunarLabel = "${lp.day}/${lp.month}$monthSuffix ÂL",
                        solarLabel = "%02d/%02d/%d".format(sd, sm, sy),
                        daysLeft = offset,
                        isMajor = true
                    )
                )
            } else if (!lp.isLeap && (lp.day == 1 || lp.day == 15) && addedSocOrVong < 2 && offset <= 32) {
                addedSocOrVong++
                val label = if (lp.day == 1) "Mùng 1 tháng ${lp.month} ÂL" else "Rằm tháng ${lp.month} ÂL"
                result.add(
                    UpcomingHoliday(
                        name = label,
                        lunarLabel = "${lp.day}/${lp.month} ÂL",
                        solarLabel = "%02d/%02d/%d".format(sd, sm, sy),
                        daysLeft = offset,
                        isMajor = false
                    )
                )
            }
            if (result.size >= limit) break
        }
        return result
    }

    // ============================================================
    // TÍNH TOÁN NGÀY TỐT XẤU ĐẦY ĐỦ (LỊCH VẠN NIÊN)
    // ============================================================
    fun getAlmanac(dd: Int, mm: Int, yy: Int): DayAlmanac {
        val lp = getLunarPoint(dd, mm, yy)
        val jd = lp.jd

        val dayCanIdx = (jd + 9) % 10
        val dayChiIdx = (jd + 1) % 12
        val monthCanIdx = (lp.year * 12 + lp.month + 3) % 10
        val monthChiIdx = (lp.month + 1) % 12
        val yearCanIdx = (lp.year + 6) % 10
        val yearChiIdx = (lp.year + 8) % 12

        val dayCanChi = "${CAN[dayCanIdx]} ${CHI[dayChiIdx]}"
        val monthCanChi = "${CAN[monthCanIdx]} ${CHI[monthChiIdx]}"
        val yearCanChi = "${CAN[yearCanIdx]} ${CHI[yearChiIdx]}"

        val sexagenaryIdx = ((jd + 49) % 60 + 60) % 60
        val napAm = NAP_AM_30[sexagenaryIdx / 2]
        val solarTerm = SOLAR_TERMS[getSolarTermIndex(jd)]

        // Hoàng Đạo / Hắc Đạo
        val startChi = ((lp.month - 1) % 6) * 2
        val star12Idx = (dayChiIdx - startChi + 12) % 12
        val isHoangDao = star12Idx in HOANG_DAO_SET
        val dayStarName = STAR12_NAMES[star12Idx]

        // 12 Trực Kiến Trừ
        val trucIdx = (dayChiIdx - monthChiIdx + 12) % 12
        val truc = TRUC_LIST[trucIdx]

        // 28 Sao Nhị Thập Bát Tú
        val saoIdx = ((jd + 4) % 28 + 28) % 28
        val sao28 = SAO28_LIST[saoIdx]

        // 6 Giờ Hoàng Đạo
        val hdHours = HOANG_DAO_HOURS_BY_CHI6[dayChiIdx % 6].joinToString(", ") { HOUR_RANGES[it] }

        // Hướng xuất hành
        val hyThan = HY_THAN[dayCanIdx]
        val taiThan = TAI_THAN[dayCanIdx]

        // Tuổi xung khắc (Lục Xung + Can khắc)
        val oppChi = (dayChiIdx + 6) % 12
        val clashCan1 = (dayCanIdx + 6) % 10
        val clashCan2 = (dayCanIdx + 4) % 10
        val conflictingAges = "${CAN[clashCan1]} ${CHI[oppChi]}, ${CAN[clashCan2]} ${CHI[oppChi]}, ${CAN[clashCan1]} ${CHI[dayChiIdx]}"

        // Các ngày đại kỵ dân gian
        val warnings = ArrayList<String>()
        if (lp.day in TAM_NUONG_DAYS) {
            warnings.add("Tam Nương (mùng ${lp.day} ÂL — kỵ khởi sự lớn)")
        }
        if (lp.day in NGUYET_KY_DAYS) {
            warnings.add("Nguyệt Kỵ (ngày ${lp.day} ÂL — kỵ xuất hành, buôn bán lớn)")
        }
        if (dayChiIdx == SAT_CHU_CHI[(lp.month - 1).coerceIn(0, 11)]) {
            warnings.add("Ngày Sát Chủ tháng ${lp.month} ÂL")
        }
        if (dayChiIdx == THO_TU_CHI[(lp.month - 1).coerceIn(0, 11)]) {
            warnings.add("Ngày Thọ Tử (tránh đại sự)")
        }

        // Đánh giá tổng quan kết hợp Hoàng Đạo + Trực + Sao 28 + Ngày kỵ
        var score = 0
        if (isHoangDao) score += 2 else score -= 1
        when (truc.quality) {
            "Rất tốt" -> score += 2
            "Tốt" -> score += 1
            "Xấu" -> score -= 2
        }
        if (sao28.isGood) score += 1 else score -= 1
        score -= warnings.size * 2

        val overallRating = when {
            score >= 4 -> "Đại Cát — Rất tốt cho công việc quan trọng"
            score >= 2 -> "Ngày Tốt — Thuận lợi cho nhiều dự định"
            score >= 0 -> "Trung Bình — Hợp việc thường ngày, chọn giờ Hoàng Đạo"
            else -> "Nên Thận Trọng — Hạn chế khởi sự lớn, giữ tâm bình an"
        }

        // Tổng hợp việc nên làm & kiêng kỵ
        val shouldDo = buildString {
            append(truc.goodFor)
            if (isHoangDao && warnings.isEmpty() && !truc.goodFor.contains("xuất hành", ignoreCase = true)) {
                append(", cầu an, gặp gỡ đối tác")
            }
        }
        val shouldAvoid = buildString {
            append(truc.badFor)
            if (warnings.isNotEmpty()) {
                append("; tránh cưới hỏi, khai trương, động thổ lớn do phạm ")
                append(warnings.joinToString(", ") { it.substringBefore(" (") })
            }
        }

        val lunarHoliday = getLunarHoliday(lp.day, lp.month, lp.isLeap, jd)
        val solarHoliday = getMajorSolarHoliday(dd, mm)
        val proverb = PROVERBS[((jd % PROVERBS.size) + PROVERBS.size) % PROVERBS.size]

        return DayAlmanac(
            solarDay = dd,
            solarMonth = mm,
            solarYear = yy,
            lunarDay = lp.day,
            lunarMonth = lp.month,
            lunarYear = lp.year,
            isLeapMonth = lp.isLeap,
            dayCanChi = dayCanChi,
            monthCanChi = monthCanChi,
            yearCanChi = yearCanChi,
            napAm = napAm,
            solarTerm = solarTerm,
            isHoangDao = isHoangDao,
            dayStarName = dayStarName,
            trucName = truc.name,
            trucQuality = truc.quality,
            trucDesc = truc.desc,
            sao28Name = sao28.name,
            sao28Good = sao28.isGood,
            sao28Desc = sao28.desc,
            hoangDaoHours = hdHours,
            hyThanDir = hyThan,
            taiThanDir = taiThan,
            conflictingAges = conflictingAges,
            badDayWarnings = warnings,
            overallRating = overallRating,
            shouldDo = shouldDo,
            shouldAvoid = shouldAvoid,
            lunarHoliday = lunarHoliday,
            solarHoliday = solarHoliday,
            proverb = proverb
        )
    }

    fun getWidgetSummaryLine(almanac: DayAlmanac, nowCal: Calendar): String {
        val dayType = if (almanac.isHoangDao) "☀ Hoàng Đạo" else "☾ Hắc Đạo"
        val holiday = almanac.lunarHoliday
        if (holiday != null) {
            return "★ $holiday • $dayType"
        }
        if (almanac.lunarDay == 1) {
            return "★ Mùng 1 Thg ${almanac.lunarMonth} ÂL • $dayType • Trực ${almanac.trucName}"
        }
        if (almanac.lunarDay == 15) {
            return "★ Rằm Thg ${almanac.lunarMonth} ÂL • $dayType • Trực ${almanac.trucName}"
        }
        val next = getUpcomingLunarHolidays(nowCal, limit = 1).firstOrNull()
        return if (next != null) {
            val shortName = next.name.substringBefore(" (")
            "$dayType • Trực ${almanac.trucName} • $shortName (${next.daysLeft}ng)"
        } else {
            "$dayType (${almanac.dayStarName}) • Trực ${almanac.trucName}"
        }
    }

    fun getWidgetTitleLine(almanac: DayAlmanac): String {
        val dayType = if (almanac.isHoangDao) {
            "☀ HOÀNG ĐẠO (${almanac.dayStarName})"
        } else {
            "☾ HẮC ĐẠO (${almanac.dayStarName})"
        }
        val saoShort = almanac.sao28Name.substringBefore(" (")
        val saoTag = if (almanac.sao28Good) "Tốt" else "Xấu"
        val holiday = almanac.lunarHoliday
        return when {
            holiday != null -> "★ $holiday • $dayType"
            almanac.lunarDay == 1 -> "★ Mùng 1 Thg ${almanac.lunarMonth} ÂL • $dayType • Trực ${almanac.trucName}"
            almanac.lunarDay == 15 -> "★ Rằm Thg ${almanac.lunarMonth} ÂL • $dayType • Trực ${almanac.trucName}"
            else -> "$dayType • Trực ${almanac.trucName} • Sao $saoShort ($saoTag)"
        }
    }

    fun getWidgetMetaLine(almanac: DayAlmanac, nowCal: Calendar): String {
        return "Ngày ${almanac.dayCanChi} (${almanac.napAm}) • Tiết ${almanac.solarTerm} • Hỷ: ${almanac.hyThanDir}, Tài: ${almanac.taiThanDir}"
    }

    fun getWidgetHoursLine(almanac: DayAlmanac): String {
        val compact = almanac.hoangDaoHours
            .replace("h)", ")")
            .replace(" (", "(")
        return "Giờ tốt: $compact"
    }

    fun getWidgetAdviceLine(almanac: DayAlmanac): String {
        val goodShort = almanac.shouldDo.split(",")
            .take(3)
            .joinToString(",") { it.trim() }
        val badShort = almanac.shouldAvoid.substringBefore(";")
            .split(",")
            .take(2)
            .joinToString(",") { it.trim() }
        return if (almanac.badDayWarnings.isNotEmpty()) {
            val warnNames = almanac.badDayWarnings.joinToString(", ") { it.substringBefore(" (") }
            "⚠ Kỵ $warnNames • Nên: $goodShort • Tránh: $badShort"
        } else {
            "Nên: $goodShort • Kỵ: $badShort"
        }
    }
}
