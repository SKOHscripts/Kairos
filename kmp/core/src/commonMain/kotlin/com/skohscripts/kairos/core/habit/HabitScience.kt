package com.skohscripts.kairos.core.habit

/** Niveau de preuve d'une affirmation (docs/spec/habitudes.md § Base scientifique). */
enum class EvidenceLevel { SOLID, MODERATE, WEAK }

/** Référence publiée : [id] (« R6 »), [citation] APA en texte (non traduite), [doi] sans préfixe (« 10.1002/ejsp.674 »). */
data class Reference(val id: String, val citation: String, val doi: String)

/** Affirmation affichée : [id] (« H1 », texte dans les ressources `habit_claim_h1`), [level], [refs] identifiants de [Reference]. */
data class Claim(val id: String, val level: EvidenceLevel, val refs: List<String>)

/**
 * Base scientifique des habitudes (docs/spec/habitudes.md § Base scientifique, issue #284) : **source unique** de l'écran
 * « Sources scientifiques » et de la page du site (`kmp/tools/make_habits_page.py`, qui lit ce fichier). Chaque référence a été
 * vérifiée le 2026-11-24 (DOI résolu, résultat relu dans le résumé ou le texte). Les textes des affirmations vivent dans les six
 * langues des ressources ; ici, seulement leurs liens aux références. `HabitScienceTest` garde : chaque affirmation a au moins
 * une référence existante, aucune référence orpheline, DOI bien formés.
 */
object HabitScience {
    val REFERENCES: List<Reference> = listOf(
        Reference("R1", "Wood, W., & Rünger, D. (2016). Psychology of habit. Annual Review of Psychology, 67, 289–314.", "10.1146/annurev-psych-122414-033417"),
        Reference(
            "R2",
            "Gardner, B., Phillips, L. A., & Judah, G. (2016). Habitual instigation and habitual execution: Definition, measurement, and effects on behaviour frequency. British Journal of Health Psychology, 21(3), 613–630.",
            "10.1111/bjhp.12189",
        ),
        Reference(
            "R3",
            "Verplanken, B., & Orbell, S. (2003). Reflections on past behavior: A self-report index of habit strength. Journal of Applied Social Psychology, 33(6), 1313–1330.",
            "10.1111/j.1559-1816.2003.tb01951.x",
        ),
        Reference(
            "R4",
            "Gardner, B., Abraham, C., Lally, P., & de Bruijn, G.-J. (2012). Towards parsimony in habit measurement: Testing the convergent and predictive validity of an automaticity subscale of the Self-Report Habit Index. International Journal of Behavioral Nutrition and Physical Activity, 9, 102.",
            "10.1186/1479-5868-9-102",
        ),
        Reference(
            "R5",
            "Gardner, B., Lally, P., & Rebar, A. L. (2024). Can—and should—automaticity be self-reported using a single item? A secondary analysis of 16 datasets. Applied Psychology: Health and Well-Being, 16(4), 2438–2457.",
            "10.1111/aphw.12600",
        ),
        Reference(
            "R6",
            "Lally, P., van Jaarsveld, C. H. M., Potts, H. W. W., & Wardle, J. (2010). How are habits formed: Modelling habit formation in the real world. European Journal of Social Psychology, 40(6), 998–1009.",
            "10.1002/ejsp.674",
        ),
        Reference(
            "R7",
            "Singh, B., Murphy, A., Maher, C., & Smith, A. E. (2024). Time to form a habit: A systematic review and meta-analysis of health behaviour habit formation and its determinants. Healthcare, 12(23), 2488.",
            "10.3390/healthcare12232488",
        ),
        Reference(
            "R8",
            "Buyalskaya, A., Ho, H., Milkman, K. L., Li, X., Duckworth, A. L., & Camerer, C. (2023). What can machine learning teach us about habit formation? Evidence from exercise and hygiene. Proceedings of the National Academy of Sciences, 120(17), e2216115120.",
            "10.1073/pnas.2216115120",
        ),
        Reference(
            "R9",
            "Keller, J., Kwasnicka, D., Klaiber, P., Sichert, L., Lally, P., & Fleig, L. (2021). Habit formation following routine-based versus time-based cue planning: A randomized controlled trial. British Journal of Health Psychology, 26(3), 807–824.",
            "10.1111/bjhp.12504",
        ),
        Reference(
            "R10",
            "Kaushal, N., & Rhodes, R. E. (2015). Exercise habit formation in new gym members: A longitudinal study. Journal of Behavioral Medicine, 38(4), 652–663.",
            "10.1007/s10865-015-9640-7",
        ),
        Reference(
            "R11",
            "Zhu, L., Tao, Y., Zhou, B., Ma, H., Kaushal, N., Ma, X., Guo, Y., Zhao, R., Oguntuase, S. B., Li, H., & Zhang, L. (2026). Not all cues are equal: A systematic review and three-level meta-analysis of context consistency, physical activity and habit strength. Applied Psychology: Health and Well-Being, 18(4), e70204.",
            "10.1111/aphw.70204",
        ),
        Reference(
            "R12",
            "Gollwitzer, P. M., & Sheeran, P. (2006). Implementation intentions and goal achievement: A meta-analysis of effects and processes. Advances in Experimental Social Psychology, 38, 69–119.",
            "10.1016/S0065-2601(06)38002-1",
        ),
        Reference(
            "R13",
            "Judah, G., Gardner, B., Kenward, M. G., DeStavola, B., & Aunger, R. (2018). Exploratory study of the impact of perceived reward on habit formation. BMC Psychology, 6, 62.",
            "10.1186/s40359-018-0270-z",
        ),
        Reference(
            "R15",
            "Ebert, J. E. J., & Lin, X. Y. (2024). Confirming the causal role of consistent contexts in developing a walking habit: A randomized comparison with varied contexts. Annals of Behavioral Medicine, 58(11), 741–751.",
            "10.1093/abm/kaae045",
        ),
        Reference(
            "R16",
            "Stawarz, K., Cox, A. L., & Blandford, A. (2015). Beyond self-tracking and reminders: Designing smartphone apps that support habit formation. Proceedings of CHI '15, 2653–2662.",
            "10.1145/2702123.2702230",
        ),
        Reference(
            "R17",
            "Renfree, I., Harrison, D., Marshall, P., Stawarz, K., & Cox, A. (2016). Don't kick the habit: The role of dependency in habit formation apps. CHI EA '16, 2932–2939.",
            "10.1145/2851581.2892495",
        ),
        Reference(
            "R21",
            "Silverman, J., & Barasch, A. (2023). On or off track: How (broken) streaks affect consumer decisions. Journal of Consumer Research, 49(6), 1095–1117.",
            "10.1093/jcr/ucac029",
        ),
        Reference(
            "R23",
            "Deci, E. L., Koestner, R., & Ryan, R. M. (1999). A meta-analytic review of experiments examining the effects of extrinsic rewards on intrinsic motivation. Psychological Bulletin, 125(6), 627–668.",
            "10.1037/0033-2909.125.6.627",
        ),
        Reference(
            "R24",
            "Harkin, B., Webb, T. L., Chang, B. P. I., Prestwich, A., Conner, M., Kellar, I., Benn, Y., & Sheeran, P. (2016). Does monitoring goal progress promote goal attainment? A meta-analysis of the experimental evidence. Psychological Bulletin, 142(2), 198–229.",
            "10.1037/bul0000025",
        ),
        Reference(
            "R26",
            "Beshears, J., Lee, H. N., Milkman, K. L., Mislavsky, R., & Wisdom, J. (2021). Creating exercise habits using incentives: The trade-off between flexibility and routinization. Management Science, 67(7), 4139–4171.",
            "10.1287/mnsc.2020.3706",
        ),
    )

    /** Les affirmations affichées, dans l'ordre (texte validé par le propriétaire le 2026-11-24). */
    val CLAIMS: List<Claim> = listOf(
        Claim("H1", EvidenceLevel.SOLID, listOf("R1", "R2")),
        Claim("H2", EvidenceLevel.MODERATE, listOf("R6", "R7", "R8")),
        Claim("H3", EvidenceLevel.MODERATE, listOf("R6")),
        Claim("H4", EvidenceLevel.MODERATE, listOf("R15", "R11", "R10")),
        Claim("H5", EvidenceLevel.MODERATE, listOf("R9")),
        Claim("H6", EvidenceLevel.WEAK, listOf("R16", "R17")),
        Claim("H7", EvidenceLevel.SOLID, listOf("R12")),
        Claim("H8", EvidenceLevel.WEAK, listOf("R7", "R26")),
        Claim("H9", EvidenceLevel.SOLID, listOf("R4", "R5", "R3")),
        Claim("H10", EvidenceLevel.WEAK, listOf("R13")),
        Claim("H11", EvidenceLevel.SOLID, listOf("R23", "R16")),
        Claim("H12", EvidenceLevel.MODERATE, listOf("R21")),
        Claim("H13", EvidenceLevel.SOLID, listOf("R24")),
    )

    /** La référence [id], ou `null`. */
    fun reference(id: String): Reference? = REFERENCES.firstOrNull { it.id == id }

    /** Lien d'un DOI. */
    fun doiUrl(doi: String): String = "https://doi.org/$doi"
}
