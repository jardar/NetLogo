// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.headless

import java.nio.file.Paths

import org.nlogo.core.{ Femto, LiteralParser, Model }
import org.nlogo.api.{ LabProtocol, ModelLoader, NetLogoLegacyDialect }
import org.nlogo.nvm.CompilerInterface
import org.nlogo.nvm.LabInterface.Settings
import org.nlogo.fileformat
import scala.util.{ Failure, Success }

import scala.io.Source

object BehaviorSpaceCoordinator {
  private val literalParser =
    Femto.scalaSingleton[LiteralParser]("org.nlogo.parse.CompilerUtilities")
  private val autoConvert =
    Femto.get[CompilerInterface]("org.nlogo.compiler.Compiler", NetLogoLegacyDialect).autoConvert _

  private lazy val loader =
    fileformat.standardLoader(literalParser, autoConvert)

  private lazy val labFormat: fileformat.NLogoLabFormat =
    new fileformat.NLogoLabFormat(autoConvert, literalParser)

  private def bsSection = labFormat.componentName

  private def modelProtocols(m: Model): Option[Seq[LabProtocol]] =
    m.optionalSectionValue[Seq[LabProtocol]](bsSection)

  def selectProtocol(settings: Settings): Option[LabProtocol] = {
    val model = modelAtPath(settings.modelPath)

    val modelWithExtraProtocols =
      settings.externalXMLFile.map { file =>
        val loadableXML = Source.fromFile(file).mkString
        val additionalProtos = labFormat.load(loadableXML.lines.toArray, None)
        model.withOptionalSection(bsSection, additionalProtos, Seq[LabProtocol]())
      }.getOrElse(model)

    val namedProtocol: Option[LabProtocol] =
      for {
        name   <- settings.protocolName
        protos <- modelProtocols(modelWithExtraProtocols)
        proto  <- protos.find(_.name == name)
      } yield proto

    lazy val firstSetupFileProtocol: Option[LabProtocol] =
      for {
        hasExternalFile <- settings.externalXMLFile
        protos          <- modelProtocols(modelWithExtraProtocols)
      } yield protos.head

    namedProtocol orElse firstSetupFileProtocol
  }

  private def modelAtPath(path: String): Model = {
    loader.readModel(Paths.get(path).toUri) match {
      case Success(m) => m
      case Failure(e) => throw new Exception("Unable to open model at: " + path + ". " + e.getMessage)
    }
  }

  def externalProtocols(path: String): Option[Seq[LabProtocol]] = {
    val fileSource = Source.fromFile(path).mkString
    labFormat.load(fileSource.lines.toArray, None)
  }

  def protocolsFromModel(modelPath: String): Seq[LabProtocol] = {
    modelProtocols(modelAtPath(modelPath)).getOrElse(Seq[LabProtocol]())
  }
}