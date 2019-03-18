

function create-branches() {

   branch=$1; shift;

   # pick first arg for github url
   github=$1; shift;

   # all the rest must be templates
   templates=($@)

   for element in "${templates[@]}"
    do
      git clone "$github/$element"
      cd $element
      
      git checkout -b "$1"
      git push --set-upstream origin "$1"
      cd ../
      rm -rf $element
    done

}
