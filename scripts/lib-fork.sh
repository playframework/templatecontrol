#!/bin/bash

function create-forks() {

   # pick first arg for github url
   github=$1; shift;

   # all the rest must be templates
   templates=($@)

   for element in "${templates[@]}"
   do
      git clone $github/$element.git
      cd $element 
      hub fork
      cd ../
      rm -rf $element
   done
}