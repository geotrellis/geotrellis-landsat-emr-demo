/**
 * If all the arguments given to this functio are defined it will evaluate curried function,
 * Else it will always evaluate curried function to undefined.
 */
export default function ifAllDefined() {
  if (! _.reduce(_.map(arguments, _.isUndefined), (a ,b) => { return a || b })){
    return f => {
      return f.apply(this, arguments)
    }
  } else {
    return f => {
      return undefined;
    }
  }
}