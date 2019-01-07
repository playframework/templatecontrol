#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

. "$DIR/templates-play.sh"
. "$DIR/lib-fork.sh"

create-forks $play_github "${play_templates[@]}"
