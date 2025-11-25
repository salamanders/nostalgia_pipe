import sys
from src.orchestrator import Orchestrator

def main():
    app = Orchestrator()

    if len(sys.argv) < 2:
        print("Usage: python main.py [submit|finalize]")
        print("  submit:   Scan files, create proxies, upload to Gemini, and analyze.")
        print("  finalize: Process analyzed jobs, transcode scenes, and generate metadata.")
        return

    command = sys.argv[1].lower()

    if command == "submit":
        app.batch_submit()
    elif command == "finalize":
        app.batch_finalize()
    else:
        print(f"Unknown command: {command}")
        print("Usage: python main.py [submit|finalize]")

if __name__ == "__main__":
    main()
