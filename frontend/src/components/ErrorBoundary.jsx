import React from 'react';
export default class ErrorBoundary extends React.Component {
  state={failed:false};
  static getDerivedStateFromError(){return {failed:true};}
  render(){return this.state.failed?<main className="empty"><h1>The store needs a refresh.</h1><p>Your saved session and guest bag remain in this tab.</p><button className="primary" onClick={()=>window.location.reload()}>Reload the store</button></main>:this.props.children;}
}
