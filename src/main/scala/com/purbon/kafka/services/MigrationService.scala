package com.purbon.kafka.services

import java.io.IOException

import com.purbon.kafka.StateManager
import com.purbon.kafka.generator.{MigrationFileWriter, MigrationGenerator}
import com.purbon.kafka.parsers.ChangeRequest
import com.purbon.kafka.readers.ChangeRequestReader

import scala.collection.mutable.ArrayBuffer

/**
  * Service used to apply migrations to the cluster
  *
  * @param reader a Change Request reader
  * @param fileStatusKeeper the status keeper
  */
class MigrationService(reader: ChangeRequestReader,
                       stateManager: StateManager) extends Service {

  override def run : Unit = {
    println(s"Running the Migration service")
    var appliedChanges = ArrayBuffer.empty[ChangeRequest]
    try {

      reader.foreach { request =>
        if (!request.name.startsWith("#")) {
          println(s"Applying ${request.name}")
          appliedChanges.append(request)
          request.up
        }
      }
      stateManager.updateLastMigration(appliedChanges.last.name)

    } catch {
      case e: Exception => {
        e.printStackTrace()
        println("Rollback applied changes")
        rollback(appliedChanges)
        stateManager.updateLastMigration("")
      }
    }
  }

  def rollback(changes : ArrayBuffer[ChangeRequest]): Unit = {
    changes.foreach { request =>
      println(s"Rollback the request ${request.name}")
      request.down()
    }
  }
}

/**
  * Migration Generation service, write down migration templates to be fill up
  * @param path The destination path for the template
  * @param migrationTypeOption The migration type (schemaMigration, topicMigration, accessMigration)
  */
class MigrationGenerationService(path: String, migrationTypeOption: Option[String]) extends Service {
  override def run: Unit = {

    migrationTypeOption match {
      case Some(migrationType:String) => {
          MigrationGenerator.generate(migrationType, writer = new MigrationFileWriter(path))
      }
      case None => {
        throw new IOException
      }
    }

  }
}


/**
  * Service used to remove migrations to the cluster
  *
  * @param changeRequestReader a Change Request reader
  * @param fileStatusKeeper the status keeper
  */
class MigrationCleanupService(changeRequestReader: ChangeRequestReader,
                       fileStatusKeeper: StateManager) extends Service {

  override def run : Unit = {
    println(s"Running the Migration clean up service")
    changeRequestReader.foreach { changeRequest =>
      if (!changeRequest.name.startsWith("#")) {
        println(changeRequest.name)
        changeRequest.down()
      }
    }
  }

}