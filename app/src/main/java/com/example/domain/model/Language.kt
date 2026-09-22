package com.example.domain.model

import java.util.Locale

/**
 * 10 Supported Languages for SIH 2026 VoxLink offline communication system.
 */
enum class Language(
    val code: String,
    val nativeName: String,
    val englishName: String,
    val sampleSentence: String,
    val emergencyPhrase: String,
    val locale: Locale,
    val modelSizeMb: Int
) {
    HINDI(
        code = "hi-IN",
        nativeName = "हिन्दी",
        englishName = "Hindi",
        sampleSentence = "नमस्ते, हम सुरक्षित स्थान पर पहुँच गए हैं।",
        emergencyPhrase = "आपातकालीन सहायता की आवश्यकता है!",
        locale = Locale("hi", "IN"),
        modelSizeMb = 38
    ),
    GUJARATI(
        code = "gu-IN",
        nativeName = "ગુજરાતી",
        englishName = "Gujarati",
        sampleSentence = "નમસ્તે, અમે સુરક્ષિત જગ્યાએ પહોંચી ગયા છીએ.",
        emergencyPhrase = "તાત્કાલિક મદદની જરૂર છે!",
        locale = Locale("gu", "IN"),
        modelSizeMb = 32
    ),
    MARATHI(
        code = "mr-IN",
        nativeName = "मराठी",
        englishName = "Marathi",
        sampleSentence = "नमस्कार, आम्ही सुरक्षित ठिकाणी पोहोचलो आहोत.",
        emergencyPhrase = "तातडीने मदतीची गरज आहे!",
        locale = Locale("mr", "IN"),
        modelSizeMb = 34
    ),
    KANNADA(
        code = "kn-IN",
        nativeName = "ಕನ್ನಡ",
        englishName = "Kannada",
        sampleSentence = "ನಮಸ್ಕಾರ, ನಾವು ಸುರಕ್ಷಿತ ಸ್ಥಳಕ್ಕೆ ತಲುಪಿದ್ದೇವೆ.",
        emergencyPhrase = "ತುರ್ತು ಸಹಾಯ ಬೇಕಾಗಿದೆ!",
        locale = Locale("kn", "IN"),
        modelSizeMb = 35
    ),
    MALAYALAM(
        code = "ml-IN",
        nativeName = "മലയാളം",
        englishName = "Malayalam",
        sampleSentence = "നമസ്കാരം, ഞങ്ങൾ സുരക്ഷിതമായ സ്ഥലത്ത് എത്തി.",
        emergencyPhrase = "അടിയന്തിര സഹായം ആവശ്യമുണ്ട്!",
        locale = Locale("ml", "IN"),
        modelSizeMb = 36
    ),
    TAMIL(
        code = "ta-IN",
        nativeName = "தமிழ்",
        englishName = "Tamil",
        sampleSentence = "வணக்கம், நாங்கள் பாதுகாப்பான இடத்தை அடைந்துவிட்டோம்.",
        emergencyPhrase = "அவசர உதவி தேவைப்படுகிறது!",
        locale = Locale("ta", "IN"),
        modelSizeMb = 37
    ),
    TELUGU(
        code = "te-IN",
        nativeName = "తెలుగు",
        englishName = "Telugu",
        sampleSentence = "నమస్కారం, మేము సురక్షిత ప్రదేశానికి చేరుకున్నాము.",
        emergencyPhrase = "అత్యవసర సహాయం కావాలి!",
        locale = Locale("te", "IN"),
        modelSizeMb = 36
    ),
    ODIA(
        code = "or-IN",
        nativeName = "ଓଡ଼ିଆ",
        englishName = "Odia",
        sampleSentence = "ନମସ୍କାର, ଆମେ ନିରାପଦ ସ୍ଥାନରେ ପହଞ୍ଚିଛୁ।",
        emergencyPhrase = "ଜରୁରୀକାଳୀନ ସାହାଯ୍ୟ ଆବଶ୍ୟକ!",
        locale = Locale("or", "IN"),
        modelSizeMb = 30
    ),
    BENGALI(
        code = "bn-IN",
        nativeName = "বাংলা",
        englishName = "Bengali",
        sampleSentence = "নমস্কার, আমরা নিরাপদ স্থানে পৌঁছেছি।",
        emergencyPhrase = "জরুরী সাহায্যের প্রয়োজন!",
        locale = Locale("bn", "IN"),
        modelSizeMb = 36
    ),
    ENGLISH(
        code = "en-IN",
        nativeName = "English",
        englishName = "English",
        sampleSentence = "Hello, we have reached the designated safe location.",
        emergencyPhrase = "Immediate emergency assistance required!",
        locale = Locale("en", "IN"),
        modelSizeMb = 28
    );

    companion object {
        fun fromCode(code: String): Language {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) || it.code.startsWith(code, ignoreCase = true) }
                ?: HINDI
        }
    }
}
