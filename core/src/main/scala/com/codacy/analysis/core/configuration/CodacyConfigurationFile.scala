package com.codacy.analysis.core.configuration

import better.files.File
import cats.syntax.show._
import com.codacy.analysis.core.files.Glob
import com.codacy.plugins.api.languages.{Language, Languages}
import io.circe.generic.auto._
import io.circe.yaml.parser
import io.circe.{Decoder, Json, _}
import play.api.libs.json.JsValue

import scala.util.{Properties, Try}

final case class LanguageConfiguration(extensions: Option[Set[String]])

final case class EngineConfiguration(excludePaths: Option[Set[Glob]],
                                     baseSubDir: Option[String],
                                     extraValues: Option[Map[String, JsValue]])

final case class CodacyConfigurationFile(engines: Option[Map[String, EngineConfiguration]],
                                         excludePaths: Option[Set[Glob]],
                                         languages: Option[Map[Language, LanguageConfiguration]]) {

  lazy val languageCustomExtensions: Map[Language, Set[String]] =
    languages.fold(Map.empty[Language, Set[String]])(_.map {
      case (lang, config) => (lang, config.extensions.getOrElse(Set.empty[String]))
    })
}

class CodacyConfigurationFileLoader {

  val filenames: Set[String] = Set(".codacy.yaml", ".codacy.yml")

  def load(directory: File): Either[String, CodacyConfigurationFile] = {
    search(directory).flatMap(configDir => parse(configDir.contentAsString))
  }

  def search(root: File): Either[String, File] = {
    filenames
      .map(root / _)
      .find(f => f.exists && f.isRegularFile)
      .fold[Either[String, File]](
        Left(s"Could not find Codacy configuration file. Make sure you have a file named like one of ${filenames
          .mkString(", ")}."))(Right(_))
  }

  def parse(yamlString: String): Either[String, CodacyConfigurationFile] = {
    for {
      json <- parser.parse(yamlString).left.map(_.show)
      cursor = HCursor.fromJson(json)
      configurationEither = Decoder[CodacyConfigurationFile].decodeAccumulating(cursor).toEither
      configuration <- configurationEither.left.map(_.toList.map(_.show).mkString(Properties.lineSeparator))
    } yield configuration
  }

}

object CodacyConfigurationFile {

  implicit val globDecoder: Decoder[Glob] = (c: HCursor) => c.as[String].map(Glob)

  implicit val languageKeyDecoder: KeyDecoder[Language] = (languageStr: String) => Languages.fromName(languageStr)

  implicit val decodeEngineConfiguration: Decoder[EngineConfiguration] =
    new Decoder[EngineConfiguration] {
      val engineConfigurationKeys = Set("enabled", "exclude_paths", "base_sub_dir")

      def apply(c: HCursor): Decoder.Result[EngineConfiguration] = {
        val extraKeys =
          c.keys.fold(List.empty[String])(_.to[List]).filter(key => !engineConfigurationKeys.contains(key))
        for {
          excludePaths <- c.downField("exclude_paths").as[Option[Set[Glob]]]
          baseSubDir <- c.downField("base_sub_dir").as[Option[String]]
        } yield {
          val extraToolConfigurations: Map[String, JsValue] = extraKeys.flatMap { extraKey =>
            c.downField(extraKey)
              .as[Json]
              .fold[Option[JsValue]](
                { _ =>
                  Option.empty
                },
                { json =>
                  Try(play.api.libs.json.Json.parse(json.noSpaces)).toOption
                })
              .map(value => (extraKey, value))
          }(collection.breakOut)

          EngineConfiguration(excludePaths, baseSubDir, Option(extraToolConfigurations).filter(_.nonEmpty))
        }
      }
    }

  implicit val decodeCodacyConfigurationFile: Decoder[CodacyConfigurationFile] =
    Decoder.forProduct3("engines", "exclude_paths", "languages")(CodacyConfigurationFile.apply)

}
