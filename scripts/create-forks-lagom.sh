#!/bin/bash

. templates-lagom.sh
. lib-fork.sh

create-forks $lagom_github "${lagom_templates[@]}"