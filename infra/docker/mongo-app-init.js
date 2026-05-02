// Mounted by docker-compose into mongodb at:
//   /docker-entrypoint-initdb.d/mongo-app-init.js
// MongoDB only runs files on first startup of an empty volume.
//
// Index creation is intentionally left to Spring Data Mongo
// (`spring.data.mongodb.auto-index-creation: true`) so the @Indexed and
// @CompoundIndex annotations on the @Document classes are the single source
// of truth. Defining the same indexes here would create them under default
// names like "userId_1", which then clash with the names Spring derives from
// the annotations (e.g. "userId") on next boot — see prior incident.
//
// File kept as a deliberate placeholder: re-add init steps here only for
// things that genuinely need to happen before the app connects (creating
// users, enabling auth, etc.).

db = db.getSiblingDB('app_db');
