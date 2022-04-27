# Datafy nav demo

Before you can `jack-in`, you need to install REBL, a experimental tool developed by cognitect.

You can download REBL [here](https://cognitect.com/dev-tools/). Once downloaded follow the enclosed
instructions to install corresponding libraries into your local maven cache.

### JDBC

For the jdbc part to work you need the [chinook.db](https://www.sqlitetutorial.net/sqlite-sample-database/) in your `resources` path.

### XTDB

For the xtdb part to work you need to first run the code in [xtdb-chinook](https://github.com/FiV0/xtdb-chinook) (ingesting chinook into xt) and create a symbolic link from the database to root of this repo.
```sh
ln -s /path/to/xtdb-chinook/data xtdb-chinook
```

## License
