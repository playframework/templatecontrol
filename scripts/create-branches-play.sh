#!/bin/bash

. templates-play.sh
. lib-create-branches.sh

  if [ $1 ]; then 
    create-branches $1 $play_github "${play_templates[@]}"
  else 
    echo "You must pass the branch name you want to create"
  fi