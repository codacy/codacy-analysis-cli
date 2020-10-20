package com.codacy.analysis.core.storage

import better.files.File
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import org.specs2.control.NoLanguageFeatures
import org.specs2.matcher.FutureMatchers
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

class WithStorageSpec extends Specification with NoLanguageFeatures with Mockito with FutureMatchers {

  case class Test(name: String)

  class StorageTest extends WithStorage[Test] {

    override val storage: FileDataStorage[Test] = new FileDataStorage[Test] {
      override implicit val encoder: Encoder[Test] = deriveEncoder
      override implicit val decoder: Decoder[Test] = deriveDecoder

      override def compare(current: Test, value: Test): Boolean = current.name == value.name

      override val storageFile: File = File.newTemporaryFile()

      override def storageFilename: String = ""
    }

    def callAdd(data: Seq[Test]): Unit = this.storage.put(data)

    def callFetch: Option[Seq[Test]] = this.storage.get()

    def disposeStorage: Boolean = this.storage.invalidate()
  }

  "WithStorage" should {
    val testingData = Seq(Test("first"), Test("second"))

    def cleanup(storageTest: StorageTest) = storageTest.disposeStorage

    s"Save and fetch storage correctly".stripMargin in {
      val storageTest = new StorageTest()
      storageTest.callAdd(testingData)

      val storageContent = storageTest.callFetch
      storageContent shouldEqual Some(testingData)
      cleanup(storageTest)
    }

    s"Dispose storage correctly".stripMargin in {
      val storageTest = new StorageTest()
      storageTest.callAdd(testingData)

      storageTest.disposeStorage shouldEqual true
      storageTest.callFetch shouldEqual None
    }

    s"Add more data to storage".stripMargin in {
      val storageTest = new StorageTest()
      storageTest.callAdd(testingData)

      val storageContent = storageTest.callFetch
      storageContent shouldEqual Some(testingData)

      // should only add Test("third") as Test("first") is already stored
      storageTest.callAdd(Seq(Test("first"), Test("third")))

      val expectedData = Seq(Test("first"), Test("third"), Test("second"))

      val newStorageContent = storageTest.callFetch
      newStorageContent shouldEqual Some(expectedData)

      cleanup(storageTest)
    }

  }
}
