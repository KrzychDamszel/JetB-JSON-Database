# JetB-JSON-Database
JetBrains - my another project.
JSON Database is a single file database that stores information in the form of JSON.
It's a remote database. Client and server can work with files.
The server keeps the database on the hard drive in a file and update only after setting a new value or deleting one.
Every client request is parsed and handled in a separate executor's task.
