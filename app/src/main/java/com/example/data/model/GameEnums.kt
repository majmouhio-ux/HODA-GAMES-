package com.example.data.model

enum class WeaponId(
    val titleAr: String,
    val code: String,
    val roleAr: String,
    val damage: Int,
    val fireRateMs: Long,
    val magSize: Int,
    val reloadMs: Long,
    val recoilKick: Float,
    val baseSpread: Float,
    val adsZoomFov: Float,
    val muzzleSpeed: Float,
    val isAutomatic: Boolean,
    val armorPenetration: Float
) {
    M4A1(
        titleAr = "بندقية M4A1 التكتيكية",
        code = "M4A1 // 5.56mm",
        roleAr = "اقتحام متوازن • دقة عالية",
        damage = 28,
        fireRateMs = 95L,
        magSize = 30,
        reloadMs = 1550L,
        recoilKick = 0.018f,
        baseSpread = 0.014f,
        adsZoomFov = 40f,
        muzzleSpeed = 165f,
        isAutomatic = true,
        armorPenetration = 0.45f
    ),
    AK47(
        titleAr = "كلاشنكوف AK-47 الفتاك",
        code = "AK-47 // 7.62mm",
        roleAr = "ضرر فتاك • اختراق دروع",
        damage = 38,
        fireRateMs = 122L,
        magSize = 30,
        reloadMs = 1750L,
        recoilKick = 0.029f,
        baseSpread = 0.021f,
        adsZoomFov = 42f,
        muzzleSpeed = 155f,
        isAutomatic = true,
        armorPenetration = 0.65f
    ),
    AWM(
        titleAr = "قناصة AWM-S بعيدة المدى",
        code = "AWM-S // .338",
        roleAr = "إصابة قاتلة • منظار 8x",
        damage = 115,
        fireRateMs = 980L,
        magSize = 7,
        reloadMs = 2200L,
        recoilKick = 0.062f,
        baseSpread = 0.0025f,
        adsZoomFov = 18f,
        muzzleSpeed = 240f,
        isAutomatic = false,
        armorPenetration = 0.92f
    ),
    M249(
        titleAr = "رشاش M249 الثقيل",
        code = "M249 SAW // 5.56mm",
        roleAr = "قمع ناري • مخزن 80 طلقة",
        damage = 26,
        fireRateMs = 80L,
        magSize = 80,
        reloadMs = 2600L,
        recoilKick = 0.022f,
        baseSpread = 0.026f,
        adsZoomFov = 45f,
        muzzleSpeed = 160f,
        isAutomatic = true,
        armorPenetration = 0.50f
    )
}

enum class AttachmentId(
    val titleAr: String,
    val bonusAr: String,
    val recoilMult: Float,
    val spreadMult: Float,
    val soundMult: Float,
    val magBonus: Int,
    val adsFovDelta: Float
) {
    HOLO_OPTIC(
        titleAr = "منظار هولوغرافي تكتيكي",
        bonusAr = "تقريب إضافي ودقة تصويب +20%",
        recoilMult = 0.92f,
        spreadMult = 0.80f,
        soundMult = 1.0f,
        magBonus = 0,
        adsFovDelta = -6f
    ),
    SUPPRESSOR(
        titleAr = "كاتم صوت عسكري",
        bonusAr = "يخفي وميض السلاح ويقلل الصوت 55%",
        recoilMult = 0.90f,
        spreadMult = 0.92f,
        soundMult = 0.45f,
        magBonus = 0,
        adsFovDelta = 0f
    ),
    EXTENDED_MAG(
        titleAr = "مخزن ذخيرة موسع",
        bonusAr = "+12 طلقة إضافية وسرعة تلقيم",
        recoilMult = 1.0f,
        spreadMult = 1.0f,
        soundMult = 1.0f,
        magBonus = 12,
        adsFovDelta = 0f
    ),
    TACTICAL_GRIP(
        titleAr = "مقبض أمامي مضاد للارتداد",
        bonusAr = "يقلل الارتداد 30% في العواصف",
        recoilMult = 0.70f,
        spreadMult = 0.85f,
        soundMult = 1.0f,
        magBonus = 0,
        adsFovDelta = 0f
    )
}

enum class TacticalId(
    val titleAr: String,
    val descAr: String,
    val pointsRequired: Int
) {
    AIRSTRIKE(
        titleAr = "غارة جوية AC-130",
        descAr = "قصف جوي مركز يدمر الأعداء والمدرعات في المنطقة",
        pointsRequired = 300
    ),
    UAV_DRONE(
        titleAr = "طائرة استطلاع UAV",
        descAr = "كشف مواقع جميع الأعداء على الرادار حتى وسط الضباب",
        pointsRequired = 180
    ),
    ARMOR_DROP(
        titleAr = "إمداد دروع وذخيرة",
        descAr = "شحن فوري لدرع كيفلر الكامل وحقيبة إسعاف",
        pointsRequired = 220
    )
}

enum class MapId(
    val titleAr: String,
    val descAr: String,
    val groundPrimaryHex: Long,
    val groundSecondaryHex: Long,
    val structureHex: Long,
    val initialWeather: WeatherType
) {
    DESERT_STORM(
        titleAr = "عاصفة الصحراء",
        descAr = "وادي صحراوي محصن بالخنادق والرادارات وعواصف الرمال",
        groundPrimaryHex = 0xFFB88D56,
        groundSecondaryHex = 0xFF8C6438,
        structureHex = 0xFF9C8468,
        initialWeather = WeatherType.CLEAR_DAY
    ),
    URBAN_SIEGE(
        titleAr = "حصار المدينة المدمرة",
        descAr = "شوارع مدمرة ومتاريس خرسانية ومعارك قريبة ضبابية",
        groundPrimaryHex = 0xFF474C52,
        groundSecondaryHex = 0xFF33373B,
        structureHex = 0xFF6B7075,
        initialWeather = WeatherType.DENSE_FOG
    ),
    ARCTIC_OUTPOST(
        titleAr = "القاعدة الجليدية",
        descAr = "حصن ثلجي وعواصف مطرية ورعدية وظلام ليلي تكتيكي",
        groundPrimaryHex = 0xFFD9E4EC,
        groundSecondaryHex = 0xFF93AAB9,
        structureHex = 0xFF5D6E7E,
        initialWeather = WeatherType.HEAVY_RAIN
    )
}

enum class GameModeId(
    val titleAr: String,
    val descAr: String,
    val enemyTotal: Int,
    val weatherIntervalSec: Float
) {
    BATTLE_ROYALE(
        titleAr = "باتل رويال كلاسيكي",
        descAr = "منطقة أمان متقلصة • 24 مقاتلاً • طقس متغير كل 30ث",
        enemyTotal = 24,
        weatherIntervalSec = 30f
    ),
    TACTICAL_SURVIVAL(
        titleAr = "موجات الحصار الشرسة",
        descAr = "قتال مكثف ضد فرق ومركبات مدرعة في طقس متقلب",
        enemyTotal = 30,
        weatherIntervalSec = 24f
    ),
    SNIPER_OPS(
        titleAr = "عمليات القنص والاستطلاع",
        descAr = "اشتباكات بعيدة المدى وسط الضباب والغسق",
        enemyTotal = 18,
        weatherIntervalSec = 28f
    )
}

enum class WeatherType(
    val titleAr: String,
    val shortCode: String,
    val impactSummaryAr: String,
    val fogDensity: Float,
    val fogColorRgb: FloatArray,
    val skyTopRgb: FloatArray,
    val skyHorizonRgb: FloatArray,
    val sunColorRgb: FloatArray,
    val sunElevationDeg: Float,
    val ambientStrength: Float,
    val rainIntensity: Float,
    val sandIntensity: Float,
    val lightningFreq: Float,
    val wetnessSpecular: Float,
    val visibilityMultiplier: Float,
    val soundPropagationMultiplier: Float,
    val movementMultiplier: Float,
    val spreadMultiplier: Float
) {
    CLEAR_DAY(
        titleAr = "نهار مشمس صافي",
        shortCode = "CLR",
        impactSummaryAr = "رؤية 100% • مدى صوت كامل • دقة مثالية",
        fogDensity = 0.0042f,
        fogColorRgb = floatArrayOf(0.68f, 0.78f, 0.88f),
        skyTopRgb = floatArrayOf(0.16f, 0.44f, 0.78f),
        skyHorizonRgb = floatArrayOf(0.70f, 0.82f, 0.92f),
        sunColorRgb = floatArrayOf(1.00f, 0.95f, 0.84f),
        sunElevationDeg = 62f,
        ambientStrength = 0.48f,
        rainIntensity = 0.0f,
        sandIntensity = 0.0f,
        lightningFreq = 0.0f,
        wetnessSpecular = 0.10f,
        visibilityMultiplier = 1.00f,
        soundPropagationMultiplier = 1.00f,
        movementMultiplier = 1.00f,
        spreadMultiplier = 1.00f
    ),
    DENSE_FOG(
        titleAr = "ضباب تكتيكي كثيف",
        shortCode = "FOG",
        impactSummaryAr = "رؤية منخفضة 32% • كتم جزئي للصوت • مثالي للرؤية الحرارية",
        fogDensity = 0.036f,
        fogColorRgb = floatArrayOf(0.56f, 0.62f, 0.66f),
        skyTopRgb = floatArrayOf(0.36f, 0.43f, 0.48f),
        skyHorizonRgb = floatArrayOf(0.58f, 0.64f, 0.68f),
        sunColorRgb = floatArrayOf(0.75f, 0.78f, 0.80f),
        sunElevationDeg = 35f,
        ambientStrength = 0.52f,
        rainIntensity = 0.0f,
        sandIntensity = 0.0f,
        lightningFreq = 0.0f,
        wetnessSpecular = 0.25f,
        visibilityMultiplier = 0.34f,
        soundPropagationMultiplier = 0.78f,
        movementMultiplier = 0.98f,
        spreadMultiplier = 1.04f
    ),
    HEAVY_RAIN(
        titleAr = "أمطار غزيرة وعواصف رعدية",
        shortCode = "RAIN",
        impactSummaryAr = "برق ورعد • تشتت صوت 45% • أرض منزلقة (-8% سرعة)",
        fogDensity = 0.022f,
        fogColorRgb = floatArrayOf(0.24f, 0.30f, 0.38f),
        skyTopRgb = floatArrayOf(0.11f, 0.15f, 0.22f),
        skyHorizonRgb = floatArrayOf(0.26f, 0.32f, 0.40f),
        sunColorRgb = floatArrayOf(0.55f, 0.65f, 0.78f),
        sunElevationDeg = 42f,
        ambientStrength = 0.34f,
        rainIntensity = 1.0f,
        sandIntensity = 0.0f,
        lightningFreq = 0.25f,
        wetnessSpecular = 0.92f,
        visibilityMultiplier = 0.54f,
        soundPropagationMultiplier = 0.55f,
        movementMultiplier = 0.92f,
        spreadMultiplier = 1.12f
    ),
    SANDSTORM(
        titleAr = "عاصفة رملية عاتية",
        shortCode = "SAND",
        impactSummaryAr = "رؤية 28% • رياح عاتية (-12% حركة، +22% تشتت) • ضجيج يخفي الخطوات",
        fogDensity = 0.044f,
        fogColorRgb = floatArrayOf(0.76f, 0.54f, 0.31f),
        skyTopRgb = floatArrayOf(0.52f, 0.34f, 0.18f),
        skyHorizonRgb = floatArrayOf(0.78f, 0.56f, 0.33f),
        sunColorRgb = floatArrayOf(0.95f, 0.68f, 0.38f),
        sunElevationDeg = 28f,
        ambientStrength = 0.44f,
        rainIntensity = 0.0f,
        sandIntensity = 1.0f,
        lightningFreq = 0.0f,
        wetnessSpecular = 0.05f,
        visibilityMultiplier = 0.28f,
        soundPropagationMultiplier = 0.48f,
        movementMultiplier = 0.88f,
        spreadMultiplier = 1.22f
    ),
    GOLDEN_DUSK(
        titleAr = "غسق حربي متغير",
        shortCode = "DUSK",
        impactSummaryAr = "شمس منخفضة • ظلال طويلة • رؤية 72%",
        fogDensity = 0.011f,
        fogColorRgb = floatArrayOf(0.74f, 0.42f, 0.28f),
        skyTopRgb = floatArrayOf(0.18f, 0.20f, 0.38f),
        skyHorizonRgb = floatArrayOf(0.84f, 0.44f, 0.24f),
        sunColorRgb = floatArrayOf(1.00f, 0.56f, 0.24f),
        sunElevationDeg = 12f,
        ambientStrength = 0.32f,
        rainIntensity = 0.0f,
        sandIntensity = 0.15f,
        lightningFreq = 0.0f,
        wetnessSpecular = 0.20f,
        visibilityMultiplier = 0.72f,
        soundPropagationMultiplier = 0.92f,
        movementMultiplier = 1.00f,
        spreadMultiplier = 1.03f
    ),
    NIGHT_STORM(
        titleAr = "ليل حالك وعاصف",
        shortCode = "NGT",
        impactSummaryAr = "ظلام دامس (رؤية 24%) • فعّل الرؤية الليلية NVG أو الحرارية",
        fogDensity = 0.025f,
        fogColorRgb = floatArrayOf(0.05f, 0.08f, 0.14f),
        skyTopRgb = floatArrayOf(0.02f, 0.03f, 0.07f),
        skyHorizonRgb = floatArrayOf(0.06f, 0.10f, 0.17f),
        sunColorRgb = floatArrayOf(0.26f, 0.34f, 0.52f),
        sunElevationDeg = 22f,
        ambientStrength = 0.16f,
        rainIntensity = 0.55f,
        sandIntensity = 0.0f,
        lightningFreq = 0.18f,
        wetnessSpecular = 0.55f,
        visibilityMultiplier = 0.24f,
        soundPropagationMultiplier = 0.65f,
        movementMultiplier = 0.95f,
        spreadMultiplier = 1.08f
    )
}

enum class OpticsMode(val titleAr: String, val code: String) {
    NORMAL("بصري عادي", "OPT"),
    NVG("رؤية ليلية NVG", "NVG"),
    THERMAL("رؤية حرارية FLIR", "FLIR")
}

enum class MatchOutcome(val titleAr: String) {
    VICTORY("نصر ساحق • آخر الناجين"),
    ELIMINATED("سقطت في ساحة المعركة"),
    EXTRACTED("انسحاب تكتيكي")
}

enum class GraphicsQuality(val titleAr: String, val particleScale: Float, val terrainStep: Int) {
    ULTRA("فائق (Ultra 3D)", 1.0f, 1),
    HIGH("عالي (High)", 0.75f, 1),
    BALANCED("متوازن (Balanced)", 0.45f, 2)
}
