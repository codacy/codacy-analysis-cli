package com.codacy.analysis.core.storage

import better.files.File
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import org.specs2.control.NoLanguageFeatures
import org.specs2.matcher.FutureMatchers
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

class FileDataStorageSpec extends Specification with NoLanguageFeatures with Mockito with FutureMatchers {

  case class Test(name: String)

  class StorageTest() extends FileDataStorage[Test]("") {
    override implicit val encoder: Encoder[Test] = deriveEncoder
    override implicit val decoder: Decoder[Test] = deriveDecoder

    override val storageFile: File = {
      val file = File.newTemporaryFile()
      file.deleteOnExit()
      file
    }
  }

  "FileDataStorage" should {
    val testingData = Seq(Test("first"), Test("second"))

    "Save and fetch storage correctly".stripMargin in {
      val storageTest = new StorageTest()
      storageTest.save(testingData)

      val storageContent = storageTest.get()
      storageContent shouldEqual Some(testingData)
    }

    "Dispose storage correctly".stripMargin in {
      val storageTest = new StorageTest()
      storageTest.save(testingData)

      storageTest.invalidate().isSuccess shouldEqual true
      storageTest.get() shouldEqual None
    }

  }
}
