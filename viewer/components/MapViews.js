// "use strict";
// import React from 'react';
// import _ from 'lodash';
// import { PanelGroup, Panel, Input, Button, ButtonGroup } from 'react-bootstrap';
// import SingleLayer from "./SingleLayer";
// import DiffLayer from "./DiffLayer";
// import DiffLayers from "./DiffLayers";
// import AverageByState from "./AverageByState";

// var Panels = React.createClass({
//   getInitialState: function () {
//     return {
//       activePane: 1,
//       autoZoom: true
//     };
//   },
//   handleAutoZoom: function(e) {
//     let v = e.target.checked || false;
//     this.setState(_.merge({}, this.state, {autoZoom: v}));
//     if (v) this.props.showExtent(this.props.layers[this.state.layerId1].extent);
//   },
//   handlePaneSelect: function(id) {
//     console.log("PANE SELECT %s", id);
//     let newState = _.merge({}, this.state, { activePane: +id });
//     this.setState(newState);
//   },
//   updateState: function(target, value) {
//     let newState = _merge({}, this.state, {[target]: value});
//     this.setState(newState);
//   },
//   showExtent: function(id) {
//     var self = this;
//     return function() {
//       if (id == self.state.activePane && self.state.autoZoom) { // if the message is from active pane, pass it on
//         self.props.showExtent.apply(this, arguments);
//       }
//     };
//   },
//   showLayerWithBreaks: function (id) {
//     var self = this;
//     return function() {
//       if (id == self.state.activePane) { // if the message is from active pane, pass it on
//         return self.props.showLayerWithBreaks.apply(self, arguments);
//       } else {
//         // console.log("Wouldn't go yonder", id, self.state.activePane, arguments)
//         return null;
//       }
//     };
//   },
//   showStateAverage: function(id) {
//     var self = this;
//     return function() {
//       if (id == self.state.activePane) { // if the message is from active pane, pass it on
//         self.props.showStateAverage.apply(this, arguments);
//       }
//     };
//   },
//   showStateDiffAverage: function(id) {
//     var self = this;
//     return function() {
//       if (id == self.state.activePane) { // if the message is from active pane, pass it on
//         self.props.showStateDiffAverage.apply(this, arguments);
//       }
//     };
//   },
//   componentDidUpdate: function(prevProps, prevState) {
//     // force map refresh if either the pane selection changed or auto-zoom was clicked
//     // this must happen after state update in order for this.showLayerWithBreaks to pass the info
//     if (this.state != prevState) {
//       switch (this.state.activePane) {
//       case 1:
//         this.refs.single.updateMap();
//         break;
//       case 2:
//         this.refs.diff.updateMap();
//         break;
//       case 3:
//         this.refs.layerDiff.updateMap();
//       case 4:
//         this.refs.averageByState.updateMap();
//       }
//     }
//   },
//   render: function() {
//     let nonLandsatLayers = _.filter(this.props.layers, l => {return ! l.isLandsat});
//     let showNEXLayers = nonLandsatLayers.length > 0;
//     return (
//     <div>
//       <Input type="checkbox" label="Snap to layer extent" checked={this.state.autoZoom} onChange={this.handleAutoZoom} />
//       <PanelGroup defaultActiveKey="1" accordion={true} onSelect={this.handlePaneSelect}>
//         <Panel header="Single Layer" eventKey="1" id={1}>
//           <SingleLayer
//             ref="single"
//             rootUrl={this.props.rootUrl}
//             layers={this.props.layers}
//             showLayerWithBreaks={this.showLayerWithBreaks(1)}
//             showExtent={this.showExtent(1)} />
//         </Panel>

//         <Panel header="Change Detection" eventKey="2" id={2}>
//           <DiffLayer
//             ref="diff"
//             rootUrl={this.props.rootUrl}
//             layers={this.props.layers}
//             showLayerWithBreaks={this.showLayerWithBreaks(2)}
//             showExtent={this.showExtent(2)} />
//         </Panel>

//         { showNEXLayers ?
//           <Panel header="Layer to Layer Change Detection" eventKey="3" id={3}>
//             <DiffLayers
//               ref="layerDiff"
//               rootUrl={this.props.rootUrl}
//               layers={nonLandsatLayers}
//               showLayerWithBreaks={this.showLayerWithBreaks(3)}
//               showExtent={this.showExtent(3)}
//               showMaxState={this.props.showMaxState}
//               hideMaxState={this.props.hideMaxState}
//               showMaxAverageState={this.props.showMaxAverageState}
//               hideMaxAverageState={this.props.hideMaxAverageState} />
//           </Panel>
//           : null
//         }

//         { showNEXLayers ?
//           <Panel header="Average by State" eventKey="4" id={4}>
//             <AverageByState
//               ref="averageByState"
//               rootUrl={this.props.rootUrl}
//               layers={nonLandsatLayers}
//               showLayerWithBreaks={this.showLayerWithBreaks(4)}
//               showStateAverage={this.showStateAverage(4)}
//               showExtent={this.showExtent(4)} />
//           </Panel>
//           : null
//         }

//         { showNEXLayers ?
//           <Panel header="Average Change by State" eventKey="4" id={4}>
//             <AverageChangeByState
//               ref="averageByState"
//               rootUrl={this.props.rootUrl}
//               layers={nonLandsatLayers}
//               showLayerWithBreaks={this.showLayerWithBreaks(4)}
//               showStateDiffAverage={this.showStateDiffAverage(4)}
//               showExtent={this.showExtent(4)} />
//           </Panel>
//           : null
//         }

//       </PanelGroup>
//     </div>)
//   }
// });

// module.exports = Panels;
