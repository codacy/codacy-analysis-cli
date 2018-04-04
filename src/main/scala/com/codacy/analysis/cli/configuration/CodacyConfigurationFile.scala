package com.codacy.analysis.cli.configuration

import com.codacy.analysis.cli.utils.Glob
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.{YAMLFactory, YAMLGenerator}
import play.api.libs.json.Reads._
import play.api.libs.json._
import com.codacy.api.dtos.{Language, Languages}

import scala.util.{Failure, Success, Try}

case class GradeConfiguration(exclude_paths: Option[Set[Glob]])

case class LanguageConfiguration(extensions: Option[Set[String]])

case class EngineConfiguration(enabled: Boolean,
                               exclude_paths: Option[Set[Glob]],
                               baseSubDir: Option[String],
                               extraValues: Option[Map[String, JsValue]])

case class CodacyConfigurationFile(engines: Option[Map[String, EngineConfiguration]],
                                   grade: Option[GradeConfiguration],
                                   exclude_paths: Option[Set[Glob]],
                                   languages: Option[Map[Language, LanguageConfiguration]])

object CodacyConfigurationFile {

  val filenames = Set(".codacy.yaml", ".codacy.yml")

  def parse(yamlString: String): Either[String, CodacyConfigurationFile] = {
    Try {
      val yamlMapper = new ObjectMapper(new YAMLFactory().configure(YAMLGenerator.Feature.WRITE_DOC_START_MARKER, false))
      val jsonString = yamlMapper.readTree(yamlString).toString
      val json = Json.parse(jsonString)
      json.validate[CodacyConfigurationFile] match {
        case JsError(error) => Left(error.mkString)
        case JsSuccess(value, _) => Right(value)
      }
    } match {
      case Failure(error) => Left(error.getMessage)
      case Success(config) => config
    }
  }

  implicit val globReads: Reads[Glob] = StringReads.map(Glob.apply)
  implicit val gradeConfigurationReads: Reads[GradeConfiguration] = Json.reads[GradeConfiguration]
  implicit val languageConfigurationReads: Reads[LanguageConfiguration] = Json.reads[LanguageConfiguration]
  implicit val engineConfigurationReads: Reads[EngineConfiguration] = Reads { json =>
    val codacyKeys = Set("enabled", "exclude_paths", "base_sub_dir")
    for {
      enabled <- (json \ "enabled").validate[Boolean]
      excludePaths = (json \ "exclude_paths").asOpt[Set[Glob]]
      baseSubDir = (json \ "base_sub_dir").asOpt[String]
      extraValuesRaw = json.asOpt[Map[String, JsValue]]
    } yield {
      val extraValues = extraValuesRaw.map(_.filterNot { case (key, _) => codacyKeys.contains(key) }).filter(_.nonEmpty)
      EngineConfiguration(enabled = enabled, excludePaths, baseSubDir, extraValues)
    }
  }

  implicit val optionSetStringReads: Reads[Option[Set[String]]] = Reads(_.validateOpt[Set[String]])

  implicit val languageMapReads: Reads[Map[Language, LanguageConfiguration]] = Reads { json =>
    json.validate[Map[String, LanguageConfiguration]].map { languages =>
      for {
        (languageName, langConfig) <- languages
        languageC <- Languages.fromName(languageName)
      } yield (languageC, langConfig)
    }
  }
  implicit val codacyConfigurationFileReads: Reads[CodacyConfigurationFile] = Json.reads[CodacyConfigurationFile]
}
