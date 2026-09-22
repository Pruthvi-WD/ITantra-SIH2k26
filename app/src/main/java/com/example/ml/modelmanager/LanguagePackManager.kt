package com.example.ml.modelmanager

import com.example.domain.model.Language
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LanguagePackInfo(
    val language: Language,
    val isInstalled: Boolean,
    val sizeMb: Int,
    val isLoadedInRam: Boolean
)

data class BenchmarkSentence(
    val language: Language,
    val category: String, // "Conversation", "Emergency", "Location", "Numbers"
    val text: String,
    val phonetics: String = ""
)

/**
 * Manages offline language packs, dynamic loading/unloading into RAM,
 * and test corpus for SIH evaluation.
 */
class LanguagePackManager {

    private val _sttLanguage = MutableStateFlow(Language.HINDI)
    val sttLanguage: StateFlow<Language> = _sttLanguage.asStateFlow()

    private val _ttsLanguage = MutableStateFlow(Language.HINDI)
    val ttsLanguage: StateFlow<Language> = _ttsLanguage.asStateFlow()

    private val _packs = MutableStateFlow(
        Language.entries.map { lang ->
            LanguagePackInfo(
                language = lang,
                isInstalled = true, // All 10 packs packaged offline
                sizeMb = lang.modelSizeMb,
                isLoadedInRam = lang == Language.HINDI
            )
        }
    )
    val packs: StateFlow<List<LanguagePackInfo>> = _packs.asStateFlow()

    fun setSttLanguage(language: Language) {
        _sttLanguage.value = language
        updateRamState()
    }

    fun setTtsLanguage(language: Language) {
        _ttsLanguage.value = language
        updateRamState()
    }

    private fun updateRamState() {
        val activeLanguages = setOf(_sttLanguage.value, _ttsLanguage.value)
        _packs.value = _packs.value.map { pack ->
            pack.copy(isLoadedInRam = activeLanguages.contains(pack.language))
        }
    }

    fun getEstimatedRamUsageMb(): Int {
        return _packs.value.filter { it.isLoadedInRam }.sumOf { it.sizeMb }
    }

    /**
     * Authentic benchmark corpus for live SIH demonstration across all 10 Indian languages.
     */
    fun getBenchmarkCorpus(): List<BenchmarkSentence> {
        return listOf(
            // Hindi
            BenchmarkSentence(Language.HINDI, "Emergency", "आपातकालीन सहायता की आवश्यकता है, कृपया तुरंत दल भेजें।"),
            BenchmarkSentence(Language.HINDI, "Conversation", "हम सुरक्षित शिविर में पहुँच गए हैं और सब कुशल हैं।"),
            BenchmarkSentence(Language.HINDI, "Location", "हम सेक्टर चार नदी के पुल के पास तैनात हैं।"),

            // Gujarati
            BenchmarkSentence(Language.GUJARATI, "Emergency", "તાત્કાલિક બચાવ ટીમની જરૂર છે, અકસ્માત થયો છે."),
            BenchmarkSentence(Language.GUJARATI, "Conversation", "નમસ્તે, અમે રાહત કેન્દ્રમાં પહોંચી ગયા છીએ."),

            // Marathi
            BenchmarkSentence(Language.MARATHI, "Emergency", "तातडीने मदतीची गरज आहे, त्वरित वैद्यकीय पथक पाठवा."),
            BenchmarkSentence(Language.MARATHI, "Conversation", "नमस्कार, आम्ही सर्वजण सुरक्षित ठिकाणी आहोत."),

            // Kannada
            BenchmarkSentence(Language.KANNADA, "Emergency", "ತುರ್ತು ವೈದ್ಯಕೀಯ ನೆರವು ಬೇಕಾಗಿದೆ, ದಯವಿಟ್ಟು ಬೇಗ ಬನ್ನಿ."),
            BenchmarkSentence(Language.KANNADA, "Conversation", "ನಮಸ್ಕಾರ, ನಾವು ಸುರಕ್ಷಿತ ಸ್ಥಳಕ್ಕೆ ತಲುಪಿದ್ದೇವೆ."),
            BenchmarkSentence(Language.KANNADA, "Location", "ನಾವು ಸೇತುವೆಯ ಹತ್ತಿರ ಕಾಯುತ್ತಿದ್ದೇವೆ."),

            // Malayalam
            BenchmarkSentence(Language.MALAYALAM, "Emergency", "അടിയന്തിര രക്ഷാപ്രവർത്തനം ആവശ്യമാണ്, വേഗം എത്തുക."),
            BenchmarkSentence(Language.MALAYALAM, "Conversation", "ഞങ്ങൾ സുരക്ഷിതമായ ദുരിതാശ്വാസ ക്യാമ്പിൽ എത്തിച്ചേർന്നു."),

            // Tamil
            BenchmarkSentence(Language.TAMIL, "Emergency", "அவசர உதவி தேவைப்படுகிறது, தயவுசெய்து விரைந்து வாருங்கள்."),
            BenchmarkSentence(Language.TAMIL, "Conversation", "வணக்கம், நாங்கள் நிவாரண முகாமை அடைந்துவிட்டோம்."),

            // Telugu
            BenchmarkSentence(Language.TELUGU, "Emergency", "అత్యవసర సహాయం కావాలి, వెంటనే సహాయక బృందాన్ని పంపండి."),
            BenchmarkSentence(Language.TELUGU, "Conversation", "నమస్కారం, మేము పునరావాస కేంద్రానికి చేరుకున్నాము."),

            // Odia
            BenchmarkSentence(Language.ODIA, "Emergency", "ଜରୁରୀକାଳୀନ ସାହାଯ୍ୟ ଆବଶ୍ୟକ, ଶୀଘ୍ର ଡାକ୍ତରୀ ଦଳ ପଠାନ୍ତୁ।"),
            BenchmarkSentence(Language.ODIA, "Conversation", "ନମସ୍କାର, ଆମେ ସୁରକ୍ଷିତ ଆଶ୍ରୟସ୍ଥଳରେ ପହଞ୍ଚିଛୁ।"),

            // Bengali
            BenchmarkSentence(Language.BENGALI, "Emergency", "জরুরী সাহায্যের প্রয়োজন, অবিলম্বে উদ্ধারকারী দল পাঠান।"),
            BenchmarkSentence(Language.BENGALI, "Conversation", "নমস্কার, আমরা নিরাপদে আশ্রয় কেন্দ্রে পৌঁছেছি।"),

            // English
            BenchmarkSentence(Language.ENGLISH, "Emergency", "Distress alert: medical evacuation required at location point alpha."),
            BenchmarkSentence(Language.ENGLISH, "Conversation", "All unit members have successfully reached base camp."),
            BenchmarkSentence(Language.ENGLISH, "Numbers", "Grid coordinates are seven four point five and one two point eight.")
        )
    }
}
