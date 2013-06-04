package com.versal.cannonball

import java.sql.Connection
import com.versal.jellyfish._

case class Entry(id: String, content: String, created: java.util.Date)

trait DbConnector {
  def connect[A](f: Connection => A): A
}

class HsqlConnector extends DbConnector {

  Class.forName("org.hsqldb.jdbcDriver")

  connect { conn =>
    val createEntries = conn.prepareCall(
      "DROP TABLE IF EXISTS ENTRIES"
    )
    createEntries.execute
    createEntries.close
  }

  connect { conn =>
    val createEntries = conn.prepareCall(
      "CREATE TABLE ENTRIES (" +
        "ID VARCHAR(128) NOT NULL, " +
        "CONTENT VARCHAR(140), " +
        "CREATED TIMESTAMP," +
        "PRIMARY KEY (ID)" +
      ")")
    createEntries.execute
    createEntries.close
  }

  override def connect[A](f: Connection => A): A = {
    import java.sql.DriverManager
    val conn = DriverManager.getConnection("jdbc:hsqldb:mem:cannonball", "SA", "")
    val x = f(conn)
    conn.close
    x
  }
}

trait IdGenerator {
  def generate: String
}

class UuidGenerator extends IdGenerator {
  override def generate: String = java.util.UUID.randomUUID.toString
}

object Queries {

  def getEntries(offset: Int, limit: Int): Connection => Seq[Entry] = { conn =>
    val selectEntries = conn.prepareStatement(
      "SELECT ID, CONTENT, CREATED FROM ENTRIES LIMIT ? OFFSET ?"
    )
    selectEntries.setInt(1, limit)
    selectEntries.setInt(2, offset)
    val results = selectEntries.executeQuery
    var entries: List[Entry] = List.empty
    while (results.next) {
      entries = Entry(results.getString(1), results.getString(2), results.getTimestamp(3)) :: entries
    }
    entries
  }

}

object Commands {

  def addEntry(id: String, content: String): Connection => Entry = { conn =>
    val insertEntry = conn.prepareStatement(
      "INSERT INTO ENTRIES (ID, CONTENT, CREATED) VALUES (?, ?, ?)"
    )
    insertEntry.setString(1, id)
    insertEntry.setString(2, content)
    val created = new java.util.Date()
    insertEntry.setTimestamp(3, new java.sql.Timestamp(created.getTime))
    insertEntry.execute
    insertEntry.close
    Entry(id, content, created)
  }
}

object Programs {

  def getEntries(offset: Int, limit: Int) = program {

    val dbConnector: DbConnector = read[DbConnector]
    val entries: Seq[Entry] = dbConnector.connect(Queries.getEntries(offset, limit))

    Return(entries)
  }

  def addEntry(content: String) = program {

    val dbConnector: DbConnector = read[DbConnector]
    val idGenerator: IdGenerator = read[IdGenerator]

    val id: String = idGenerator.generate
    val entry: Entry = dbConnector.connect(Commands.addEntry(id, content))

    Return(entry)
  }

}

object Views {

  import scala.xml.{NodeSeq, PrettyPrinter}

  lazy val printer = new PrettyPrinter(160, 2)

  def entry(entry: Entry): NodeSeq =
    <html>
      <head>
        <title>Cannonball</title>
      </head>
      <body>
        <h1>Entry {entry.id}</h1>
        <h2>{entry.created}</h2>
        <p>{entry.content}</p>
      </body>
    </html>

  def entries(entries: Seq[Entry]): NodeSeq =
    <html>
      <head>
        <title>Cannonball</title>
      </head>
      <body>
        <h1>Entries</h1>
        { entries.map(toXml) }
      </body>
    </html>

  def toXml(entry: Entry): NodeSeq =
    <div>{entry.content} @ {entry.created}</div>

  def pretty(nodes: NodeSeq): String = 
    printer.formatNodes(nodes)
}

import javax.servlet.http.HttpServlet

class CannonballServlet extends HttpServlet {

  import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

  val dbConnector = new HsqlConnector
  val idGenerator = new UuidGenerator

  def run[A](p: Program): Any = p match {
    case Return(a) => a
    case With(c, f) if c.isA[DbConnector] => run(f(dbConnector))
    case With(c, f) if c.isA[IdGenerator] => run(f(idGenerator))
  }

  override def doPost(req: HttpServletRequest, res: HttpServletResponse) {
    Option(req.getParameter("content")) match {
      case Some(content) => val entry: Entry = run(Programs.addEntry(content)).asInstanceOf[Entry]
                            res.getWriter.write(Views.pretty(Views.entry(entry)))
      case None          => res.sendError(400, "missing content")
    } 
  }

  override def doGet(req: HttpServletRequest, res: HttpServletResponse) {
    val entries: Seq[Entry] = run(Programs.getEntries(0, 20)).asInstanceOf[Seq[Entry]]
    res.getWriter.write(Views.pretty(Views.entries(entries)))
  }

}
