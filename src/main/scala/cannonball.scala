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

  import scala.xml.NodeSeq

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

}

import javax.servlet.http.HttpServlet

class CannonballServlet extends HttpServlet {

  import scala.xml.{NodeSeq, PrettyPrinter}
  import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

  val dependencies: Map[Class[_], Any] =
    Map(classOf[DbConnector] -> new HsqlConnector,
        classOf[IdGenerator] -> new UuidGenerator)

  val prettyPrinter = new PrettyPrinter(160, 2)

  def pretty(nodes: NodeSeq): String = 
    prettyPrinter.formatNodes(nodes)

  def run(p: Program): Any = p match {
    case Return(a)  => a
    case With(c, f) =>
      val dependency: Any = dependencies(c)
      val nextProgram: Program = f(dependency)
      run(nextProgram)
  }

  override def doPost(req: HttpServletRequest, res: HttpServletResponse) {
    Option(req.getParameter("content")) match {
      case None => res.sendError(400, "missing content")
      case Some(content) =>
        val program: Program = Programs.addEntry(content)
        val entry: Entry = run(program).asInstanceOf[Entry]
        val view: NodeSeq = Views.entry(entry)
        res.getWriter.write(pretty(view))
    } 
  }

  override def doGet(req: HttpServletRequest, res: HttpServletResponse) {
    val program: Program = Programs.getEntries(0, 20)
    val entries: Seq[Entry] = run(program).asInstanceOf[Seq[Entry]]
    val view: NodeSeq = Views.entries(entries)
    res.getWriter.write(pretty(view))
  }

}
