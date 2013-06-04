# Cannonball

Cannonball is a Web application that demonstrates using [Jellyfish](https://github.com/Versal/jellyfish) for dependency injection.  It is named for the delicious [Cannonball Jellyfish](https://en.wikipedia.org/wiki/Cannonball_jellyfish).

## What it does

Cannonball implements a very simple single-user micro blog.  Entries can be added in an HTTP POST, and retrieved with a HTTP GET.

To run, Cannonball needs a couple dependencies: a way to connect to a database, and a way to generate unique entry identifiers:

```scala
trait DbConnector {
  def connect[A](f: Connection => A): A
}

trait IdGenerator {
  def generate: String
}
```

These dependencies are used within `Program` implementations:

```scala
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
```

A basic type-based pattern match injects the dependencies and continues the program:

```scala
def run[A](p: Program): Any = p match {
  case Return(a) => a
  case With(c, f) if c.isA[DbConnector] => run(f(dbConnector))
  case With(c, f) if c.isA[IdGenerator] => run(f(idGenerator))
}
```

## Usage

First, fire up sbt:

```
$ sbt
```

Then launch Cannonball with the `container:start` sbt command:

```
> container:start
```

Once Cannonball is running, throw some requests at it:

```
$ curl localhost:8080 -d 'content=Hello world!'
<html>
  <head>
    <title>Cannonball</title>
  </head>
  <body>
    <h1>Entry e25deba7-0f51-4b68-a81a-3178636c5911</h1>
    <h2>Tue Jun 04 16:03:35 PDT 2013</h2>
    <p>Hello, world!</p>
  </body>
</html>

$ curl localhost:8080 -d 'content=Dependency injection!'
<html>
  <head>
    <title>Cannonball</title>
  </head>
  <body>
    <h1>Entry 32b27b04-8ce4-4b58-a8eb-3f8373b483ed</h1>
    <h2>Tue Jun 04 16:04:23 PDT 2013</h2>
    <p>Dependency injection!</p>
  </body>
</html>

$ curl localhost:8080
<html>
  <head>
    <title>Cannonball</title>
  </head>
  <body>
    <h1>Entries</h1>
    <div>Hello, world! @ 2013-06-04 16:03:35.861</div>
    <div>Dependency injection! @ 2013-06-04 16:04:23.781</div>
  </body>
</html>
```
