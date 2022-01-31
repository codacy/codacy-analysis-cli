package com.codacy.analysis.core.storage

import better.files.File
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import org.specs2.control.NoLanguageFeatures
import org.specs2.matcher.FutureMatchers
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.core.AsExecution

class FileDataStorageSpec extends Specification with NoLanguageFeatures with Mockito with FutureMatchers {

  case class Test(name: String)
  implicit val encoder: Encoder[Test] = deriveEncoder
  implicit val decoder: Decoder[Test] = deriveDecoder

  def usingTempFile[R: AsExecution](f: DataStorage[Test] => R): R = {
    File.temporaryFile() { file =>
      val storageTest = FileDataStorage[Test](file)
      f(storageTest)
    }
  }

  val storageFile: File = {
    val file = File.newTemporaryFile()
    file.deleteOnExit()
    file
  }

  val storageTest = FileDataStorage[Test](storageFile)

  "FileDataStorage" should {
    val testingData = Seq(Test("first"), Test("second"))

    "Save and fetch storage correctly".stripMargin in {
      usingTempFile { storageTest =>
        storageTest.save(testingData)

        val storageContent = storageTest.get()
        storageContent shouldEqual Some(testingData)
      }
    }

    "Dispose storage correctly".stripMargin in {
      usingTempFile { storageTest =>
        storageTest.save(testingData)

        storageTest.invalidate().isSuccess shouldEqual true
        storageTest.get() shouldEqual None
      }
    }

  }
}
