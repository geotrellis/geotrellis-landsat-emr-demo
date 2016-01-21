import React from 'react';
import { Input, Button } from 'react-bootstrap';

var Catalog = React.createClass({
  getInitialState: function () {
    return {
      url: this.props.defaultUrl
    };
  },
  handleSubmit: function() {
    this.props.onSubmit(this.state.url);
  },
  handleOnChange: function (ev) {
    this.setState({url: ev.target.value});
  },
  handleKeyDown: function(ev) {
    if (ev.keyCode == 13) {
      this.handleSubmit();
    }
  },
  render: function() {
    const goButton = <Button onClick={this.handleSubmit}>Load</Button>;
    return (
      <div>
        <Input type="text"
          ref="catalog"
          defaultValue={this.props.defaultUrl}
          groupClassName="group-class"
          wrapperClassName="wrapper-class"
          labelClassName="label-class"
          buttonAfter={goButton}
          onChange={this.handleOnChange}
          onKeyDown={this.handleKeyDown}/>
      </div>
    );
  }
});

module.exports = Catalog;
