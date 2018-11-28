#!/bin/bash

for element in "${templates[@]}"
do
   git clone $github/$element.git
   cd $element 
   hub fork
   cd ../
   rm -rf $element
done