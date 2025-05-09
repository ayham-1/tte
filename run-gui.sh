#!/bin/bash

DRI_PRIME=1 page.codeberg.terratactician_expandoria.game --bot localhost:7738 --challenge --recorder-file last_game.json --report-file report.json --graphic "$@"

cat report.json | jq
