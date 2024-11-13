## scratch (TODO formalize)

### pre-reqs
```
Temporal Service (Cloud or local)
'just' cli app
```

### web
```
cd web
npm install
poetry install
poetry shell
```

Copy `.env.template` to `.env` and update the values as needed.

Run the web server:
```
just run_web
```

Run the Temporal caller API and worker
```
just run_temporal
```