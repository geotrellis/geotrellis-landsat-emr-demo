"use strict";
import React from 'react';
import _ from 'lodash';
import { PanelGroup, Panel, Input, Button, ButtonGroup } from 'react-bootstrap';
import ifAllDefined from "../utils/utils";

function updateSingleLayerMap (showLayerWithBreaks, showLayer, root, op, layer, t1) {
  // Single Band Calculation
  let time1 = layer.times[t1];
  if(layer.isLandsat) {
    let opc = (op != "none") ?  `&operation=${op}` : "";
    showLayer(`${root}/tiles/${layer.name}/{z}/{x}/{y}?time=${time1}${opc}`);
  } else {
    showLayerWithBreaks(
      `${root}/tiles/${layer.name}/{z}/{x}/{y}?time=${time1}`,
      `${root}/tiles/breaks/${layer.name}?time=${time1}`
    );
  }

};

var SingleLayer = React.createClass({
  getInitialState: function () {
    return {
      operation: "none",
      layerId: undefined, // layer index
      timeId: undefined,  // time index in layer
      times: {}           // maps from layerId => {timeId1 , timeId2}
    };
  },
  handleLayerSelect: function(ev) {
    let layerId = +ev.target.value;
    let newState = _.merge({}, this.state, {
      "layerId": layerId,
      "time": _.get(this.state.times[layerId], "time", undefined),
      "times": { // Saves time selectio when switching layer
        [this.state.layerId]: {
          "time": this.state.time
        }
      }
    });

    this.setState(newState);
    this.updateMap(newState);
    this.props.showExtent(this.props.layers[layerId].extent);
  },
  updateState: function(target, value) {
    let newState = _.merge({}, this.state, {[target]: value});
    this.setState(newState);
    this.updateMap(newState);
  },
  ping: function () {
      alert("PING");
  },
  updateMap: function (state) {
    if (! state) { state = this.state; }
    ifAllDefined(this.props.showLayerWithBreaks, this.props.showLayer, this.props.rootUrl, state.operation, this.props.layers[state.layerId], state.timeId)
      (updateSingleLayerMap);
    this.props.showExtent(this.props.layers[state.layerId].extent);
  },
  componentWillReceiveProps: function (nextProps){
  /** Use this as an opportunity to react to a prop transition before render() is called by updating the state using this.setState().
    * The old props can be accessed via this.props. Calling this.setState() within this function will not trigger an additional render. */
    if ( _.isUndefined(this.state.layerId) && ! _.isEmpty(nextProps.layers)) {
      // we are blank and now is our chance to choose a layer and some times
      let newState = _.merge({}, this.state, { layerId: 0, timeId: 0 });
      this.setState(newState);
      var layer = nextProps.layers[0];
      updateSingleLayerMap(nextProps.showLayerWithBreaks, nextProps.showLayer, nextProps.rootUrl, this.state.operation, layer, 0);
      nextProps.showExtent(layer.extent);
    }
  },
  render: function() {
    let layer       = this.props.layers[this.state.layerId];
    let isLandsat   = _.get(layer, "isLandsat", false);

    let layerOptions =
      _.map(this.props.layers, (layer, index) => {
        return <option value={index} key={index}>{layer.name}</option>;
      });

    let layerTimes =
      _.map(_.get(layer, "times", []), (time, index) => {
        return <option value={index} key={index}>{time}</option>;
      });

    return (
      <div>
        <Input type="select" label="Layer" placeholder="select" value={this.state.layerId}
          onChange={e => this.handleLayerSelect(e)}>
          {layerOptions}
        </Input>

        <Input type="select" label="Time" placeholder="select" value={this.state.timeId}
            onChange={e => this.updateState("timeId", +e.target.value)}>
          {layerTimes}
        </Input>

        <Input type="select" label="Operation" placeholder="select" defaultValue="none"
            value={isLandsat ? this.state.bandOp : "none"}
            disabled={!isLandsat}
            onChange={e => this.updateState("operation", e.target.value)}>
          <option value="none">View</option>
          <option value="ndvi">NDVI</option>
          <option value="ndwi">NDWI</option>
        </Input>
      </div>
    )
  }
});

module.exports = SingleLayer;
