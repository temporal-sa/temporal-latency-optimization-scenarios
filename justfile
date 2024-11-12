set dotenv-load := true

codegen:
    npm run codegen --prefix web

kill_temporal:
    -@killall temporal

local_temporal:
    if test ! -n "$(lsof -i :7233)"; then $(temporal server start-dev); fi

run_web:
    @echo "Starting web at $PUBLIC_WEB_URL"
    cd web && poetry run python main.py

run_temporal:
    @echo "Starting Temporal worker and caller API on $CALLER_API_PORT"
    cd temporal-java && ./gradlew api --console=plain