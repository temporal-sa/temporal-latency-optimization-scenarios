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

Run the web server:
```
just run_web
```

Run the Temporal caller API and worker
```
just run_temporal
```