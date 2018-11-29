#!/bin/bash

. templates-play.sh
. lib-fork.sh

create-forks $play_github "${play_templates[@]}"