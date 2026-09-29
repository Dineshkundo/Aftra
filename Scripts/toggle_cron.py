
import sys

source, destination, expected, action = sys.argv[1:]
tag = "#JENKINS_DISABLED "

with open(source, encoding="utf-8") as f:
    lines = f.readlines()

result = []
matches = 0

for original in lines:
    line = original

    if action == "COMMENT":
        if line.lstrip().startswith("#"):
            result.append(line)
            continue

    elif action == "UNCOMMENT":
        if not line.startswith(tag):
            result.append(line)
            continue
        line = line[len(tag):]

    else:
        raise SystemExit("ERROR: Invalid action")

    fields = line.split(None, 5)

    if len(fields) != 6:
        result.append(original)
        continue

    command = fields[5].strip()

    if (
        command == expected
        or command.startswith(expected + " ")
        or command.startswith(expected + "\t")
    ):
        matches += 1
        if action == "COMMENT":
            line = tag + line

    result.append(line)

if matches != 1:
    raise SystemExit(
        f"ERROR: Expected exactly one matching cron entry; "
        f"found {matches}. No changes installed."
    )

with open(destination, "w", encoding="utf-8") as f:
    f.writelines(result)

print(f"Validated exactly one entry for {action}.")
