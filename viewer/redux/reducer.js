var reducer = function (state, action) {
  switch (action.type) {
    case 'SET_ANALYSIS_LAYER':
      return Object.assign({}, state, { analysisLayer: action.layer });
    case 'SET_NDI':
      return Object.assign({}, state, { ndi: action.ndi });
    case 'SET_LAYER_TYPE':
      return Object.assign({}, state, { layerType: action.layerType });
    case 'REGISTER_TIME':
      var updatedTimes = state.times;
      updatedTimes[state.layerName][action.index] = action.time;
      return Object.assign({}, state, { times: updatedTimes });
    case 'SET_LAYERNAME':
      var delta = {
        layerName: action.name,
        times: {}
      };
      if (! state.times[action.name]) { delta.times[action.name] = []; }
      return Object.assign({}, state, delta);
    case 'SHOW_LAYER':
      return Object.assign({}, state, {
        map: {
          url: [action.url],
          activeLayerId: action.id
        }
      });
    case 'CENTER_MAP':
      return Object.assign({}, state, {
        map: { extent: action.extent }
      });
    case 'LOAD_CATALOG_SUCCESS': {
      // On new catalog, set layer to first in list; times to the corresponding times
      var layer = action.catalog.layers[0];
      var times = {};
      times[layer.name] = layer.times;
      return Object.assign({}, state, {
        rootUrl: action.url,
        catalog: action.catalog,
        layerName: layer.name,
        times: times // set this to be equal to times - values are updated later
      });
    }
    case 'SHOW_BOUNDS': {
      return _.merge({}, state, { map: { bounds: action.bounds } });
    }
    case 'SHOW_MAX_STATE': {
      console.log("SHOW_MAX_STATE");
      console.log(action.geojson);
      return _.merge({}, state, { map: { maxState: action.geojson } });
    }
    case 'HIDE_MAX_STATE': {
      console.log("HIDE_MAX_STATE");
      return _.merge({}, state, { map: { maxState: null } });
    }
    case 'SHOW_MAX_AVERAGE_STATE': {
      console.log("SHOW_MAX_AVERAGE_STATE");
      console.log(action.geojson);
      return _.merge({}, state, { map: { maxAverageState: action.geojson } });
    }
    case 'HIDE_MAX_AVERAGE_STATE': {
      console.log("HIDE_MAX_AVERAGE_STATE");
      return _.merge({}, state, { map: { maxAverageState: null } });
    }
    case 'SHOW_STATE_AVERAGE': {
      console.log("SHOW_STATE_AVERAGE");
      console.log(action.geojson);
      return _.merge({}, state, { map: { stateAverage: action.geojson } });
    }
    case 'SHOW_STATE_DIFF_AVERAGE': {
      console.log("SHOW_STATE_DIFF_AVERAGE");
      console.log(action.geojson);
      return _.merge({}, state, { map: { stateDiffAverage: action.geojson } });
    }
    default:
      return state;
  }
};

module.exports = reducer;
