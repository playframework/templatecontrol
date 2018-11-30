#!/bin/bash

. templates-lagom.sh
. lib-create-branches.sh

  if [ $1 ]; then 
    create-branches $1 $lagom_github "${lagom_templates[@]}"
  else 
    echo "You must pass the branch name you want to create"
  fi