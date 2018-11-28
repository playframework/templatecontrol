
if [ $1 ]; then 
  for element in "${templates[@]}"
  do
      git clone "$github/$element"
      cd $element
      
      git checkout -b "$1"
      git push --set-upstream origin "$1"
      cd ../
      rm -rf $element
  done
else 
  echo "You must pass the branch name you want to create"
fi
