import React from 'react';
import { render } from 'react-dom';
import { Map, Marker, Popup, TileLayer, BaseTileLayer, GeoJson } from 'react-leaflet';
import "leaflet/dist/leaflet.css";

var Leaflet = React.createClass({
  render: function() {
    const style = {
      minHeight: "800px", width: "100%"
    };
    let tileLayers = _.map(this.props.url, u => {
      return <TileLayer key={1} url={u} />;
    });

    let vectorLayers = [];
    if(this.props.maxState) {
      vectorLayers.push(<GeoJson data={this.props.maxState} />);
    }

    if(this.props.maxAverageState) {
      let state = this.props.maxAverageState;
      vectorLayers.push(
        <GeoJson data={state}
        fillOpacity={0.0}
        color="#333300"
        weight={3}>
          <Popup>
            <div>
              <p><b>Name:</b> {state.properties.name}</p>
              <p><b>Average Temperature:</b> {state.properties["Mean Temperature"]} °F</p>
            </div>
          </Popup>
        </GeoJson>
      );
    }

    if(this.props.stateAverage) {
      let state = this.props.stateAverage;
      vectorLayers.push(
        <GeoJson data={state}
        fillOpacity={0.0}
        color="#333300"
        weight={3}>
          <Popup>
            <div>
              <p><b>Name:</b> {state.properties.name}</p>
              <p><b>Average Temperature:</b> {parseFloat(Math.round(state.properties.meanTemp * 100) / 100).toFixed(2)} °F</p>
            </div>
          </Popup>
        </GeoJson>
      );
    }

    if(this.props.stateDiffAverage) {
      let state = this.props.stateDiffAverage;
      vectorLayers.push(
        <GeoJson data={state}
        fillOpacity={0.0}
        color="#333300"
        weight={3}>
          <Popup>
            <div>
              <p>Name: <b>{state.properties.name}</b></p>
              <p>Average Difference of Temperature: <b>{state.properties.meanTemp} °F</b></p>
            </div>
          </Popup>
        </GeoJson>
      );
    }

    console.log(vectorLayers);

    return (
      <Map center ={[37.062,-121.530]} zoom={8} style={style} bounds={this.props.bounds}>
        <TileLayer
          url="http://{s}.basemaps.cartocdn.com/light_nolabels/{z}/{x}/{y}.png"
        />
        {tileLayers}
        {vectorLayers}
      </Map>
    );
  }
});

module.exports = Leaflet;
