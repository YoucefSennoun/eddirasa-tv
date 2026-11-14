package com.youcef.eddirasatv

import androidx.annotation.StringRes

data class Section(
    @StringRes val titleRes: Int,
    val url: String,
    val badge: String
)

object Sections {
    const val BASE_URL = "https://eddirasa.com/"

    val all: List<Section> = listOf(
        Section(R.string.section_primary, "https://eddirasa.com/ens-pri/", "ب"),
        Section(R.string.section_middle, "https://eddirasa.com/ens-cm/", "م"),
        Section(R.string.section_secondary, "https://eddirasa.com/ens-sec/", "ث"),
        Section(R.string.section_university, "https://eddirasa.com/ens-uni/", "ج"),
        Section(R.string.section_exams, "https://eddirasa.com/exams/", "ا"),
        Section(R.string.section_average, "https://eddirasa.com/average/", "ح"),
        Section(R.string.section_teachers, "https://eddirasa.com/teachers/", "أ"),
        Section(R.string.section_training, "https://eddirasa.com/training/", "ت"),
        Section(R.string.section_questions, "https://eddirasa.com/questions/", "س"),
        Section(R.string.section_classrooms, "https://eddirasa.com/classrooms/", "غ"),
        Section(R.string.section_news, "https://eddirasa.com/news/", "خ"),
        Section(R.string.section_search, "https://eddirasa.com/search/", "بحث")
    )
}
